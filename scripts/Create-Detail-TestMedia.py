from pathlib import Path
import struct,math

def vint(n):
    width=next(w for w in range(1,9) if n<(1<<(7*w))-1)
    return (n|(1<<(7*width))).to_bytes(width,'big')
def el(tag,value):
    if isinstance(value,int):value=value.to_bytes(max(1,(value.bit_length()+7)//8),'big')
    if isinstance(value,str):value=value.encode()
    return bytes.fromhex(tag)+vint(len(value))+value
header=el('1a45dfa3',el('4286',1)+el('42f7',1)+el('42f2',4)+el('42f3',8)+el('4282','matroska')+el('4287',4)+el('4285',2))
info=el('1549a966',el('2ad7b1',1000000)+el('4489',struct.pack('>d',20000))+el('4d80','ETKN synthetic fixture')+el('5741','ETKN test'))
tracks=b''
for number in (1,2):
    tracks+=el('ae',el('d7',number)+el('73c5',number)+el('83',2)+el('86','A_PCM/INT/LIT')+el('22b59c','chi')+el('536e',f'Audio {number}')+el('e1',el('b5',struct.pack('>d',8000))+el('9f',1)+el('6264',16)))
for number in (3,4):
    tracks+=el('ae',el('d7',number)+el('73c5',number)+el('83',17)+el('86','S_TEXT/UTF8')+el('22b59c','chi')+el('536e',f'Subtitle {number}'))
clusters=b''
for second in range(20):
    blocks=el('e7',second*1000)
    for frame in range(10):
        for track in (1,2):
            audio=b''.join(struct.pack('<h',int(1800*math.sin(2*math.pi*(220*track)*(i+800*frame)/8000))) for i in range(800))
            blocks+=el('a3',bytes([0x80|track])+struct.pack('>h',frame*100)+b'\x80'+audio)
    for track in (3,4):blocks+=el('a0',el('a1',bytes([0x80|track])+b'\0\0\0'+f'Synthetic subtitle {track}'.encode())+el('9b',1000))
    clusters+=el('1f43b675',blocks)
Path('app/src/androidTest/assets/detail-tracks.mkv').write_bytes(header+el('18538067',info+el('1654ae6b',tracks)+clusters))
print('Created isolated synthetic Matroska with two same-language audio and subtitle tracks')
