"""«Яблочный дозор» — MVP Telegram-бота для теста на 6 недель.

Садовод: фото → геометка → объём → заявка. Оператор (команда) видит каждую заявку,
ставит класс качества кнопкой («ручная» оценка в MVP), собирает рейсы и вносит вес приёмки.
Подсказка по фото считается простой эвристикой по цвету; она помогает оператору, но не заменяет его.

Запуск:  pip install -r requirements.txt
         BOT_TOKEN=... ADMIN_IDS=123456789 python bot.py
"""
import asyncio
import csv
import io
import json
import logging
import math
import os
import sqlite3
from datetime import datetime

import qrcode
from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.filters import Command, CommandObject, CommandStart
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.types import (BufferedInputFile, CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup,
                           KeyboardButton, Message, ReplyKeyboardMarkup, ReplyKeyboardRemove)
from PIL import Image

# ---------- настройки ----------
TOKEN = os.environ.get("BOT_TOKEN", "")
ADMIN_IDS = {int(x) for x in os.environ.get("ADMIN_IDS", "").replace(" ", "").split(",") if x}
DB_PATH = os.environ.get("DB_PATH", "dozor.sqlite3")
PRICE_GARDEN = float(os.environ.get("PRICE_GARDEN", 10))   # ₽/кг садоводу
PRICE_PROC = float(os.environ.get("PRICE_PROC", 22))       # ₽/кг платит переработчик
MIN_RUN_KG = float(os.environ.get("MIN_RUN_KG", 1000))     # рейс собирается от этого веса
CLUSTER_KM = float(os.environ.get("CLUSTER_KM", 10))       # радиус кластера
PROCESSOR = (float(os.environ.get("PROCESSOR_LAT", 57.715)), float(os.environ.get("PROCESSOR_LON", 27.915)))
RATE_H, RATE_KM = 1200, 35                                 # тарифы «Газели», Псков, 10.2026

GRADES = {"table": "столовое", "tech": "техническое", "fallen": "падалица", "rot": "гниль — не берём"}
VOLUMES = {"40": "1–2 мешка, ≈ 40 кг", "120": "3–5 мешков, ≈ 120 кг", "250": "6–10 мешков, ≈ 250 кг", "500": "больше, ≈ 500 кг"}

logging.basicConfig(level=logging.INFO)
router = Router()


# ---------- база ----------
def db() -> sqlite3.Connection:
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    con.executescript("""
    create table if not exists offers(
      id integer primary key, user_id integer, username text, lat real, lon real, place text,
      kg_est real, damage_pct integer, grade text, status text default 'new', run_id integer,
      kg_fact real, photo_id text, created_at text);
    create table if not exists runs(id integer primary key, slot text, km real, hours integer, truck_rub integer, created_at text);
    """)
    return con


def km(a, b) -> float:
    r, to = 6371.0, math.pi / 180
    dla, dlo = (b[0] - a[0]) * to, (b[1] - a[1]) * to
    h = math.sin(dla / 2) ** 2 + math.cos(a[0] * to) * math.cos(b[0] * to) * math.sin(dlo / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


# ---------- подсказка по фото: доля бурых пикселей ----------
def damage_share(data: bytes) -> tuple[int, int]:
    img = Image.open(io.BytesIO(data)).convert("RGB")
    img.thumbnail((240, 240))
    apple = brown = 0
    for r, g, b in img.getdata():
        mx, mn = max(r, g, b), min(r, g, b)
        if mx == 0:
            continue
        s, v = (mx - mn) / mx, mx / 255
        if s < 0.3 or v < 0.12:
            continue
        if mx == mn:
            h = 0.0
        elif mx == r:
            h = (60 * ((g - b) / (mx - mn))) % 360
        elif mx == g:
            h = 60 * ((b - r) / (mx - mn)) + 120
        else:
            h = 60 * ((r - g) / (mx - mn)) + 240
        if 8 <= h <= 45 and v < 0.55:
            brown += 1
        elif h < 55 or h > 330:
            apple += 1
    total = apple + brown
    return (round(brown / total * 100) if total else -1), total


# ---------- кластеры и маршрут ----------
def clusters(rows):
    """Жадная группировка: заявки в радиусе CLUSTER_KM от первой заявки группы."""
    left, groups = list(rows), []
    while left:
        seed = left.pop(0)
        group = [seed] + [o for o in left if km((seed["lat"], seed["lon"]), (o["lat"], o["lon"])) <= CLUSTER_KM]
        left = [o for o in left if o not in group]
        groups.append(group)
    return sorted(groups, key=lambda g: -sum(o["kg_est"] for o in g))


def route(group):
    cur, todo, order, dist = PROCESSOR, list(group), [], 0.0
    while todo:
        nxt = min(todo, key=lambda o: km(cur, (o["lat"], o["lon"])))
        dist += km(cur, (nxt["lat"], nxt["lon"]))
        cur = (nxt["lat"], nxt["lon"])
        order.append(nxt)
        todo.remove(nxt)
    dist += km(cur, PROCESSOR)
    road = dist * 1.3  # поправка на дороги
    hours = max(2, math.ceil(0.5 + len(group) * 10 / 60 + road / 40))
    return order, road, hours, hours * RATE_H + round(road) * RATE_KM


def is_admin(m) -> bool:
    return m.from_user and m.from_user.id in ADMIN_IDS


# ---------- садовод ----------
class Offer(StatesGroup):
    photo = State()
    place = State()
    volume = State()
    confirm = State()


MAIN_KB = ReplyKeyboardMarkup(keyboard=[[KeyboardButton(text="🍎 Сдать яблоки")], [KeyboardButton(text="ℹ️ Как это работает")]],
                              resize_keyboard=True)


@router.message(CommandStart())
async def start(m: Message, state: FSMContext):
    await state.clear()
    await m.answer("Здравствуйте! Я «Яблочный дозор» 🍎\nЗаберу лишние яблоки у калитки и передам переработчику. "
                   f"Вам — <b>{PRICE_GARDEN:g} ₽ за кг</b>, без поездок.", reply_markup=MAIN_KB)


@router.message(F.text == "ℹ️ Как это работает")
async def how(m: Message):
    await m.answer("1. Пришлите фото — оценим качество.\n2. Отправьте геопозицию сада и примерный объём.\n"
                   f"3. Когда у соседей наберётся {MIN_RUN_KG:g} кг, пришлём день вывоза и QR-бирки на мешки.\n"
                   "4. Переработчик взвесит мешки — деньги придут по СБП.\n\nГниль не берём: машина не поедет за ней зря.")


@router.message(F.text == "🍎 Сдать яблоки")
async def ask_photo(m: Message, state: FSMContext):
    await state.set_state(Offer.photo)
    await m.answer("Пришлите 1 фото яблок при дневном свете.", reply_markup=ReplyKeyboardRemove())


@router.message(Offer.photo, F.photo)
async def got_photo(m: Message, state: FSMContext, bot: Bot):
    buf = await bot.download(m.photo[-1])
    pct, n = damage_share(buf.read())
    await state.update_data(photo_id=m.photo[-1].file_id, damage_pct=pct)
    hint = (f"Подсказка по фото: бурых пятен ≈ {pct}%." if n > 200 else "На фото мало красных и жёлтых яблок — оценит оператор.")
    kb = ReplyKeyboardMarkup(keyboard=[[KeyboardButton(text="📍 Отправить геопозицию", request_location=True)]],
                             resize_keyboard=True, one_time_keyboard=True)
    await state.set_state(Offer.place)
    await m.answer(f"{hint}\nТочный класс поставит оператор после проверки.\n\nГде сад? Нажмите кнопку ниже.", reply_markup=kb)


@router.message(Offer.photo)
async def need_photo(m: Message):
    await m.answer("Нужна именно фотография яблок 📷")


@router.message(Offer.place, F.location)
async def got_place(m: Message, state: FSMContext):
    await state.update_data(lat=m.location.latitude, lon=m.location.longitude)
    kb = InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text=t, callback_data=f"vol:{k}")] for k, t in VOLUMES.items()])
    await state.set_state(Offer.volume)
    await m.answer("Сколько примерно яблок? Точный вес определим на приёмке.", reply_markup=kb)


@router.message(Offer.place)
async def need_place(m: Message):
    await m.answer("Нажмите «📍 Отправить геопозицию». Если не получается — попросите внука-«дозорного» или напишите нам.")


@router.callback_query(Offer.volume, F.data.startswith("vol:"))
async def got_volume(c: CallbackQuery, state: FSMContext):
    kg = float(c.data.split(":")[1])
    await state.update_data(kg_est=kg)
    kb = InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text="✅ Подтвердить", callback_data="ok"),
                                                InlineKeyboardButton(text="✏️ Заново", callback_data="again")]])
    await state.set_state(Offer.confirm)
    await c.message.edit_text(f"≈ {kg:g} кг × {PRICE_GARDEN:g} ₽ = <b>{kg * PRICE_GARDEN:,.0f} ₽</b> (точно — по весу на приёмке).".replace(",", " "),
                              reply_markup=kb)


@router.callback_query(Offer.confirm, F.data == "again")
async def again(c: CallbackQuery, state: FSMContext):
    await state.set_state(Offer.photo)
    await c.message.edit_text("Хорошо, пришлите фото ещё раз.")


@router.callback_query(Offer.confirm, F.data == "ok")
async def save(c: CallbackQuery, state: FSMContext, bot: Bot):
    d = await state.get_data()
    con = db()
    cur = con.execute("insert into offers(user_id,username,lat,lon,kg_est,damage_pct,photo_id,created_at) values(?,?,?,?,?,?,?,?)",
                      (c.from_user.id, c.from_user.username or c.from_user.full_name, d["lat"], d["lon"], d["kg_est"],
                       d["damage_pct"], d["photo_id"], datetime.now().isoformat(timespec="minutes")))
    con.commit()
    oid = cur.lastrowid
    await state.clear()
    await c.message.edit_text(f"Заявка №{oid} принята ✅ Пришлём день вывоза, когда у соседей наберётся {MIN_RUN_KG:g} кг.")
    await c.message.answer("Спасибо! Можно сдать ещё.", reply_markup=MAIN_KB)
    grade_kb = InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text=t, callback_data=f"g:{oid}:{k}")] for k, t in GRADES.items()])
    for admin in ADMIN_IDS:
        await bot.send_photo(admin, d["photo_id"], caption=f"Заявка №{oid}: ≈ {d['kg_est']:g} кг, подсказка — бурых {d['damage_pct']}%.\n"
                             f"Координаты: {d['lat']:.5f}, {d['lon']:.5f}\nПоставьте класс:", reply_markup=grade_kb)


# ---------- оператор ----------
@router.callback_query(F.data.startswith("g:"))
async def set_grade(c: CallbackQuery, bot: Bot):
    if not is_admin(c):
        return await c.answer("Только для оператора", show_alert=True)
    _, oid, grade = c.data.split(":")
    con = db()
    status = "rejected" if grade == "rot" else "ready"
    con.execute("update offers set grade=?, status=? where id=?", (grade, status, int(oid)))
    con.commit()
    row = con.execute("select * from offers where id=?", (int(oid),)).fetchone()
    await c.message.edit_caption(caption=f"{c.message.caption}\n→ {GRADES[grade]}")
    text = ("К сожалению, партию не возьмём: много гнили. Можно отобрать целые яблоки и прислать новое фото."
            if grade == "rot" else f"Заявка №{oid}: класс — <b>{GRADES[grade]}</b>. Ждём, пока наберётся рейс.")
    await bot.send_message(row["user_id"], text)


@router.message(Command("clusters"))
async def show_clusters(m: Message):
    if not is_admin(m):
        return
    rows = db().execute("select * from offers where status='ready' and run_id is null").fetchall()
    if not rows:
        return await m.answer("Готовых заявок нет.")
    lines = []
    for i, g in enumerate(clusters(rows), 1):
        kg = sum(o["kg_est"] for o in g)
        flag = "✅ можно собирать" if kg >= MIN_RUN_KG else f"не хватает {MIN_RUN_KG - kg:g} кг"
        lines.append(f"{i}. {len(g)} заявок, ≈ {kg:g} кг — {flag}")
    await m.answer("Кластеры (радиус %g км):\n%s\n\nСобрать: /run 1 четверг 10:00–12:00" % (CLUSTER_KM, "\n".join(lines)))


@router.message(Command("run"))
async def make_run(m: Message, command: CommandObject, bot: Bot):
    if not is_admin(m):
        return
    args = (command.args or "").split(maxsplit=1)
    if not args or not args[0].isdigit():
        return await m.answer("Формат: /run <номер кластера> <день и время>")
    con = db()
    rows = con.execute("select * from offers where status='ready' and run_id is null").fetchall()
    groups = clusters(rows)
    idx = int(args[0]) - 1
    if not 0 <= idx < len(groups):
        return await m.answer("Нет такого кластера. Посмотрите /clusters")
    slot = args[1] if len(args) > 1 else "дата уточняется"
    order, road, hours, truck = route(groups[idx])
    cur = con.execute("insert into runs(slot,km,hours,truck_rub,created_at) values(?,?,?,?,?)",
                      (slot, round(road, 1), hours, truck, datetime.now().isoformat(timespec="minutes")))
    run_id = cur.lastrowid
    for o in order:
        con.execute("update offers set run_id=?, status='scheduled' where id=?", (run_id, o["id"]))
    con.commit()
    kg = sum(o["kg_est"] for o in order)
    stops = "\n".join(f"{i}. №{o['id']} — https://yandex.ru/maps/?pt={o['lon']},{o['lat']}&z=15" for i, o in enumerate(order, 1))
    await m.answer(f"Рейс №{run_id}: {len(order)} точек, ≈ {kg:g} кг, ≈ {road:.0f} км, {hours} ч.\n"
                   f"«Газель»: {truck} ₽. Маржа по оценке: {kg * (PRICE_PROC - PRICE_GARDEN) - truck:,.0f} ₽\n\n{stops}".replace(",", " "))
    for o in order:
        bags = max(1, round(o["kg_est"] / 40))
        await bot.send_message(o["user_id"], f"🚚 Рейс назначен: <b>{slot}</b>. Подготовьте мешки. Ниже QR-бирки — наклейте по одной на мешок "
                                             f"или напишите маркером «{o['id']}».")
        for b in range(1, min(bags, 10) + 1):
            img = qrcode.make(f"ЯД|заявка {o['id']}|мешок {b}/{bags}|рейс {run_id}")
            out = io.BytesIO()
            img.save(out, format="PNG")
            await bot.send_photo(o["user_id"], BufferedInputFile(out.getvalue(), f"qr_{o['id']}_{b}.png"), caption=f"Мешок {b} из {bags}")


@router.message(Command("intake"))
async def intake(m: Message, command: CommandObject, bot: Bot):
    """Вес на приёмке: /intake <номер заявки> <кг>"""
    if not is_admin(m):
        return
    try:
        oid, kg = (command.args or "").split()
        oid, kg = int(oid), float(kg.replace(",", "."))
    except ValueError:
        return await m.answer("Формат: /intake 12 118.5")
    con = db()
    row = con.execute("select * from offers where id=?", (oid,)).fetchone()
    if not row:
        return await m.answer("Нет такой заявки")
    con.execute("update offers set kg_fact=?, status='accepted' where id=?", (kg, oid))
    con.commit()
    await bot.send_message(row["user_id"], f"✅ Приёмка: {kg:g} кг × {PRICE_GARDEN:g} ₽ = <b>{kg * PRICE_GARDEN:,.0f} ₽</b>. "
                                           "Перевод по СБП придёт от переработчика. Спасибо!".replace(",", " "))
    await m.answer(f"Заявка №{oid}: {kg:g} кг записано.")


@router.message(Command("act"))
async def act(m: Message, command: CommandObject):
    """Закупочный акт по рейсу в CSV (поля по форме ОП-5): /act <номер рейса>"""
    if not is_admin(m):
        return
    if not (command.args or "").strip().isdigit():
        return await m.answer("Формат: /act 1")
    rows = db().execute("select * from offers where run_id=? and kg_fact is not null", (int(command.args),)).fetchall()
    out = io.StringIO()
    w = csv.writer(out, delimiter=";")
    w.writerow(["№", "Продавец (Telegram)", "Заявка", "Класс", "Вес, кг", "Цена, ₽/кг", "Сумма, ₽"])
    total_kg = total = 0.0
    for i, r in enumerate(rows, 1):
        s = r["kg_fact"] * PRICE_GARDEN
        total_kg += r["kg_fact"]
        total += s
        w.writerow([i, r["username"], r["id"], GRADES.get(r["grade"], "—"), r["kg_fact"], PRICE_GARDEN, round(s, 2)])
    w.writerow(["", "Итого", "", "", total_kg, "", round(total, 2)])
    await m.answer_document(BufferedInputFile(out.getvalue().encode("utf-8-sig"), f"akt_reis_{command.args}.csv"),
                            caption="Черновик закупочного акта. ФИО и паспортные данные продавцов вносит переработчик.")


@router.message(Command("export"))
async def export(m: Message):
    """GeoJSON всех заявок — для карты переработчика (Leaflet / geojson.io)."""
    if not is_admin(m):
        return
    feats = [{"type": "Feature", "geometry": {"type": "Point", "coordinates": [r["lon"], r["lat"]]},
              "properties": {"id": r["id"], "kg": r["kg_est"], "grade": r["grade"], "status": r["status"]}}
             for r in db().execute("select * from offers").fetchall()]
    data = json.dumps({"type": "FeatureCollection", "features": feats}, ensure_ascii=False).encode()
    await m.answer_document(BufferedInputFile(data, "offers.geojson"))


@router.message(Command("stats"))
async def stats(m: Message):
    if not is_admin(m):
        return
    con = db()
    n = con.execute("select count(*) from offers").fetchone()[0]
    kg = con.execute("select coalesce(sum(kg_est),0) from offers where status!='rejected'").fetchone()[0]
    fact = con.execute("select coalesce(sum(kg_fact),0) from offers").fetchone()[0]
    rej = con.execute("select count(*) from offers where status='rejected'").fetchone()[0]
    runs = con.execute("select count(*), coalesce(sum(truck_rub),0) from runs").fetchone()
    per_kg = f"{runs[1] / fact:.2f} ₽/кг" if fact else "—"
    await m.answer(f"Заявок: {n} (отклонено как гниль: {rej})\nВ заявках: ≈ {kg:g} кг\nПринято по весу: {fact:g} кг\n"
                   f"Рейсов: {runs[0]}, логистика: {runs[1]} ₽ → {per_kg}")


async def main():
    if not TOKEN:
        raise SystemExit("Задайте BOT_TOKEN (получить у @BotFather) и ADMIN_IDS (ваш Telegram ID)")
    bot = Bot(TOKEN, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    dp.include_router(router)
    db().close()
    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())
