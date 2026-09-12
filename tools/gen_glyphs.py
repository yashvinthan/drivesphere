import re

with open('app/src/main/java/com/example/ui/screens/GlyphMatrix.kt') as f:
    code = f.read()

patterns = re.findall(r'val (GLYPH_\w+) = listOf\((.*?)\n\)', code, re.DOTALL)
for name, body in patterns:
    lines = []
    for raw in body.strip().splitlines():
        s = raw.strip()
        if s.startswith('"'):
            # extract string inside quotes
            idx1 = s.find('"')
            idx2 = s.rfind('"')
            if idx1 != -1 and idx2 > idx1:
                lines.append(s[idx1+1:idx2])
    rows_hex = []
    for line in lines[:12]:
        val = 0
        padded = line.ljust(14)
        for i in range(14):
            ch = padded[i]
            if ch != ' ':
                val |= (1 << (13 - i))
        rows_hex.append(f'0x{val:04X}')
    print(f'const uint16_t {name}[12] = {{ {", ".join(rows_hex)} }};')
