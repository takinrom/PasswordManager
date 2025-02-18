from flask import Flask, request, g, make_response, abort
from random import randint
import sqlite3

app = Flask(__name__)


DATABASE = './database.db'
DB_INIT_SCRIPT = '''CREATE TABLE IF NOT EXISTS data (id INTEGER NOT NULL PRIMARY KEY,
                                                     service TEXT NOT NULL,
                                                     login TEXT NOT NULL,
                                                     encrypted_password TEXT NOT NULL);'''

SECRET = 'HTTP Auth token'

@app.route("/logins", methods=["GET"])
def logins():
    if request.method != 'GET':
        abort(405)
    auth = request.headers.get('Authorization') 
    if auth == None or auth != SECRET:
        abort(401)
    
    return query_db('SELECT service, login FROM data')

@app.route('/pass', methods=['GET'])
def get_pass():
    if request.method != 'GET':
        abort(405)
    auth = request.headers.get('Authorization') 
    if auth == None or auth != SECRET:
        abort(401)
    service = request.args['service']
    login = request.args['login']
    res = query_db('SELECT encrypted_password FROM data WHERE service = ? AND login = ?', (service, login), True)
    if res is not None:
        return res[0]
    abort(404)

@app.route("/add", methods=["POST"])
def add():
    if request.method != 'POST':
        abort(405)
    auth = request.headers.get('Authorization') 
    if auth == None or auth != SECRET:
        abort(401)
    service = request.form['service']
    login = request.form['login']
    tmp = query_db('SELECT id FROM data WHERE service = ? AND login = ?', (service, login))
    if tmp is not None and len(tmp) != 0:
        abort(409)
    encrypted_password = request.form['encrypted_password']
    query_db('INSERT INTO data (service, login, encrypted_password) VALUES (?, ?, ?)', (service, login, encrypted_password))
    return "OK"


def get_db():
    db = getattr(g, '_database', None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
    return db

@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, '_database', None)
    if db is not None:
        db.commit()
        db.close()

def init_db():
    with app.app_context():
        db = get_db()
        db.executescript(DB_INIT_SCRIPT)
        db.commit()

def query_db(query, args=(), one=False):
    cur = get_db().execute(query, args)
    rv = cur.fetchall()
    cur.close()
    return (rv[0] if rv else None) if one else rv

init_db()
