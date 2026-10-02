__license__ = 'GPL 3'
from itertools import cycle
from calibre.customize.conversion import InputFormatPlugin

ADOBE_OBFUSCATION = 'http://ns.adobe.com/pdf/enc#RC'
IDPF_OBFUSCATION = 'http://www.idpf.org/2008/embedding'


def decrypt_font_data(key, data, algorithm):
    is_adobe = algorithm == ADOBE_OBFUSCATION
    crypt_len = 1024 if is_adobe else 1040
    crypt = bytearray(data[:crypt_len])
    key = cycle(iter(bytearray(key)))
    decrypt = bytes(bytearray(x ^ next(key) for x in crypt))
    return decrypt + data[crypt_len:]


def decrypt_font(key, path, algorithm):
    with open(path, 'r+b') as f:
        data = decrypt_font_data(key, f.read(), algorithm)
        f.seek(0), f.truncate(), f.write(data)


class EPUBInput(InputFormatPlugin):
    name = 'EPUB Input'
    author = 'Kovid Goyal'
    description = _('Convert EPUB files (.epub) to HTML')
    file_types = {'epub', 'kepub'}
    output_encoding = None
    commit_name = 'epub_input'
