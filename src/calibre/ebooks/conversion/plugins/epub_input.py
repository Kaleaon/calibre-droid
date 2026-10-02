ADOBE_OBFUSCATION = 'http://ns.adobe.com/pdf/enc#gxf'
IDPF_OBFUSCATION = 'http://www.idpf.org/2008/embedding'

def decrypt_font_data(key, data, algorithm):
    from itertools import cycle
    is_adobe = algorithm == ADOBE_OBFUSCATION
    crypt_len = 1024 if is_adobe else 1040
    crypt = bytearray(data[:crypt_len])
    key = cycle(iter(bytearray(key)))
    decrypt = bytes(bytearray(x^next(key) for x in crypt))
    return decrypt + data[crypt_len:]
