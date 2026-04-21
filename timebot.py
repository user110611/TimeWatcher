import discord
from discord.ext import commands, tasks
from discord import app_commands
import sqlite3
import os
from datetime import datetime

# ===== НАСТРОЙКИ =====
BOT_TOKEN = ""
DB_PATH = "/data/data/com.termux/files/home/minecraft/plugins/TimeWatcher/data.db"
ALLOWED_ROLE_ID = 1491888723263229982
NOTIFY_USER_ID = 1109604751999508492
# =====================

intents = discord.Intents.default()
intents.members = True
bot = discord.Client(intents=intents)
tree = app_commands.CommandTree(bot)

def get_db():
    return sqlite3.connect(DB_PATH)

def init_db():
    db = get_db()
    cur = db.cursor()
    cur.execute("""CREATE TABLE IF NOT EXISTS playtime (
        uuid TEXT PRIMARY KEY, name TEXT, seconds INTEGER DEFAULT 0)""")
    cur.execute("""CREATE TABLE IF NOT EXISTS tokens (
        uuid TEXT PRIMARY KEY, tokens REAL DEFAULT 0)""")
    cur.execute("""CREATE TABLE IF NOT EXISTS links (
        discord_id TEXT PRIMARY KEY, minecraft_nick TEXT, confirmed INTEGER DEFAULT 0)""")
    cur.execute("""CREATE TABLE IF NOT EXISTS pending_links (
        minecraft_nick TEXT PRIMARY KEY, discord_id TEXT, discord_name TEXT)""")

    # Таблица для хранения настроек бота
    # Здесь хранится время последнего снятия токенов
    # Благодаря этому снятие происходит ровно раз в 24 часа
    # даже если бот перезапускался из-за перебоев с интернетом
    cur.execute("""CREATE TABLE IF NOT EXISTS bot_config (
        key TEXT PRIMARY KEY, value TEXT)""")

    db.commit()
    db.close()
    print("БД инициализирована")

def has_role(interaction: discord.Interaction) -> bool:
    if not interaction.guild:
        return False
    role = interaction.guild.get_role(ALLOWED_ROLE_ID)
    return role in interaction.user.roles if role else False

# ===== /link =====
@tree.command(name="link", description="Привязать Discord к Minecraft нику")
@app_commands.describe(minecraft_nick="Ник в Minecraft")
async def link(interaction: discord.Interaction, minecraft_nick: str):
    if not has_role(interaction):
        await interaction.response.send_message("❌ Нет доступа!", ephemeral=True)
        return

    discord_id = str(interaction.user.id)
    discord_name = str(interaction.user.name)
    db = get_db()
    cur = db.cursor()

    cur.execute("SELECT name FROM playtime WHERE name = ?", (minecraft_nick,))
    if not cur.fetchone():
        await interaction.response.send_message(
            f"❌ Игрок `{minecraft_nick}` не найден!\nОн должен хотя бы раз зайти на сервер.",
            ephemeral=True)
        db.close()
        return

    cur.execute("""INSERT INTO pending_links (minecraft_nick, discord_id, discord_name)
        VALUES (?, ?, ?)
        ON CONFLICT(minecraft_nick) DO UPDATE SET discord_id = ?, discord_name = ?""",
        (minecraft_nick, discord_id, discord_name, discord_id, discord_name))
    db.commit()
    db.close()

    await interaction.response.send_message(
        f"✅ Запрос отправлен!\nЗайди в Minecraft и пропиши **`/link bot`** чтобы подтвердить.",
        ephemeral=True)

# ===== /mystats =====
@tree.command(name="mystats", description="Посмотреть статистику на сервере")
async def mystats(interaction: discord.Interaction):
    if not has_role(interaction):
        await interaction.response.send_message("❌ Нет доступа!", ephemeral=True)
        return

    discord_id = str(interaction.user.id)
    db = get_db()
    cur = db.cursor()

    cur.execute("SELECT minecraft_nick, confirmed FROM links WHERE discord_id = ?", (discord_id,))
    row = cur.fetchone()
    if not row:
        await interaction.response.send_message(
            "❌ Аккаунт не привязан! Используй `/link <ник>`", ephemeral=True)
        db.close()
        return

    nick, confirmed = row
    if not confirmed:
        await interaction.response.send_message(
            "⏳ Привязка не подтверждена!\nЗайди в Minecraft и пропиши **`/link bot`**",
            ephemeral=True)
        db.close()
        return

    cur.execute("SELECT seconds FROM playtime WHERE name = ?", (nick,))
    time_row = cur.fetchone()
    seconds_total = time_row[0] if time_row else 0

    cur.execute("""SELECT tokens FROM tokens
        WHERE uuid = (SELECT uuid FROM playtime WHERE name = ?)""", (nick,))
    token_row = cur.fetchone()
    tokens = round(token_row[0], 2) if token_row else 0
    db.close()

    hours = seconds_total // 3600
    minutes = (seconds_total % 3600) // 60
    seconds = seconds_total % 60

    token_str = f"`{tokens}`" if tokens >= 0 else f"🔴 `{tokens}`"

    embed = discord.Embed(title=f"📊 Статистика {nick}", color=0xFFAA00, timestamp=datetime.now())
    embed.add_field(name="⏰ Время на сервере",
                    value=f"`{hours}ч. {minutes}м. {seconds}с.`", inline=False)
    embed.add_field(name="💎 Токены", value=token_str, inline=False)
    embed.set_footer(text="ClanMine Statistics")

    await interaction.response.send_message(embed=embed, ephemeral=True)

# ===== Снятие 0.25 токена каждые 24 часа =====
# Проверяем каждые 5 минут — прошло ли 24 часа с последнего снятия.
# Время последнего снятия хранится в БД, поэтому даже если бот
# перезапустился из-за перебоев с интернетом — снятие не пропустится
# и не выполнится дважды.
@tasks.loop(minutes=5)
async def daily_deduct():
    db = get_db()
    cur = db.cursor()

    # Смотрим когда последний раз снимали токены
    cur.execute("SELECT value FROM bot_config WHERE key = 'last_deduct'")
    row = cur.fetchone()
    last_deduct = float(row[0]) if row else 0

    now = datetime.now().timestamp()

    # Если с последнего снятия прошло меньше 24 часов — ничего не делаем
    if now - last_deduct < 86400:
        db.close()
        return

    # Снимаем 0.25 токена у всех игроков
    cur.execute("UPDATE tokens SET tokens = tokens - 0.25")

    # Сохраняем время снятия в БД
    cur.execute("""INSERT INTO bot_config (key, value) VALUES ('last_deduct', ?)
        ON CONFLICT(key) DO UPDATE SET value = ?""",
        (str(now), str(now)))

    db.commit()

    # Ищем игроков у которых баланс -1 и ниже
    cur.execute("""SELECT p.name, t.tokens FROM tokens t
        JOIN playtime p ON t.uuid = p.uuid
        WHERE t.tokens <= -1""")
    bad_players = cur.fetchall()
    db.close()

    print(f"[{datetime.now()}] Ежедневное снятие токенов выполнено")

    # Отправляем уведомление если есть должники
    if bad_players:
        try:
            user = await bot.fetch_user(NOTIFY_USER_ID)
            if user:
                message = "⚠️ **Игроки с балансом -1 и ниже:**\n"
                for nick, tokens in bad_players:
                    message += f"• `{nick}` — **{round(tokens, 2)}** токенов\n"
                await user.send(message)
        except Exception as e:
            print(f"Ошибка отправки уведомления: {e}")

@daily_deduct.before_loop
async def before_daily_deduct():
    await bot.wait_until_ready()

@bot.event
async def on_ready():
    init_db()
    await tree.sync()
    daily_deduct.start()
    print(f"Бот запущен: {bot.user}")

    # Показываем когда будет следующее снятие токенов
    db = get_db()
    cur = db.cursor()
    cur.execute("SELECT value FROM bot_config WHERE key = 'last_deduct'")
    row = cur.fetchone()
    db.close()

    if row:
        last = float(row[0])
        next_deduct = last + 86400
        remaining = next_deduct - datetime.now().timestamp()
        hours_left = int(remaining // 3600)
        minutes_left = int((remaining % 3600) // 60)
        print(f"Следующее снятие токенов через: {hours_left}ч {minutes_left}м")
    else:
        print("Первое снятие токенов произойдёт через 24 часа")

bot.run(BOT_TOKEN)