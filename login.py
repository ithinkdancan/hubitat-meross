import base64
import hashlib
import string
import random
import requests
import time

def rand_gen(size, chars=string.ascii_lowercase + string.digits):
    return str(''.join(random.choice(chars) for _ in range(size)))

def msg_id(unix_time):
    concat_string = '{}{}'.format(rand_gen(16), unix_time)
    final_md5 = hashlib.md5(concat_string.encode('utf-8')).hexdigest()
    return str(final_md5)

def get_unix_time():
    current_time = int(time.time())
    return current_time

def get_key(username, password, uts):

    nonce = rand_gen
    unix_time = uts

    param = '{{"email":"{}","password":"{}"}}'.format(username, password)
    encoded_param = base64.standard_b64encode(param.encode('utf8'))

    concat_sign = '{}{}{}{}'.format('23x17ahWarFH6w29', unix_time, nonce, encoded_param.decode("utf-8"))
    sign = hashlib.md5(concat_sign.encode('utf-8')).hexdigest()

    headers = {
    'content-type': 'application/x-www-form-urlencoded',
    }
    data = {
    'params': encoded_param,
    'sign': sign,
    'timestamp': unix_time,
    'nonce': nonce
    }
    response = requests.post('https://iot.meross.com/v1/Auth/login', headers=headers, data=data)
    key = response.json()['data']['key']
    userid = response.json()['data']['userid']
    token = response.json()['data']['token']
    return str(key), str(userid), str(token)

def signing_key(message_id, key, uts):
    concat_string = '{}{}{}'.format(message_id, key, uts)
    final_md5 = hashlib.md5(concat_string.encode('utf-8')).hexdigest()
    return str(final_md5)


def login(username, password):
    current = get_unix_time()
    message_id = msg_id(current)

    key, userid, token = get_key(username, password, current)
    sign = signing_key(message_id,key, current)

    print("{} {}".format("userId:", userid))
    print("{} {}".format("key:", key))
    print("{} {}".format("token:", token))
    print("{} {}".format("messageId:", message_id))
    print("{} {}".format("sign:", sign))
    print("{} {}".format("timestamp:", current))


email = input("email: ")
password = input("password: ")

login(email,password)