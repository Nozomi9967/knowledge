import re, os

dir_path = r'C:\Users\q1948\Desktop\八股\算法题\题单'

# Load slug map
slug_map = {}
with open(r'C:\Users\q1948\Desktop\八股\raw\clippings\slug_map.txt', 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if '=' in line:
            num, slug = line.split('=', 1)
            slug_map[num.strip()] = slug.strip()

lcp_lcr_map = {
    "LCP07": "chuan-di-xin-xi",
    "LCP09": "zui-xiao-tiao-yue-ci-shu",
    "LCR114": "Jf1JuT",
    "LCP21": "Za25hA",
    "LCP35": "DFPeFJ",
    "LCP54": "s5kipK",
    "LCP04": "broken-board-dominoes",
    "LCP38": "7rLGCR",
    "LCP16": "you-le-yuan-de-you-lan-ji-hua",
    "LCP81": "ryfUiz",
}

# Patterns using raw strings
# r'\\.' in regex = literal backslash + any char (matches the \. in file content)
PAT_NUM = r'(\d+)\\.\s*(.+)'
PAT_LCP  = r'(LCP|LCR)\s*(\d+)\\.\s*(.+)'
PAT_IV   = r'面试题\s*(\d+)\.(\d+)\\.\s*(.+)'

files = [f for f in os.listdir(dir_path) if f.endswith('.md')]
total = 0

for fname in files:
    filepath = os.path.join(dir_path, fname)
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    count = 0
    new_lines = []
    for line in lines:
        if not line.startswith('- [ ] '):
            new_lines.append(line)
            continue

        content = line[len('- [ ] '):].rstrip('\n')

        # Pattern: "3370\. 题名 suffix"
        m = re.match(PAT_NUM, content)
        if m:
            num, rest = m.group(1), m.group(2)
            slug = slug_map.get(num)
            if slug:
                new_line = f'- [ ] [{num}. {rest}](https://leetcode.cn/problems/{slug}/)\n'
                new_lines.append(new_line)
                count += 1
                continue

        # Pattern: "LCP 07\. 题名" or "LCR 114\. 题名"
        m = re.match(PAT_LCP, content, re.IGNORECASE)
        if m:
            prefix, num, rest = m.group(1).upper(), m.group(2), m.group(3)
            key = f"{prefix}{num}"
            slug = lcp_lcr_map.get(key)
            if slug:
                new_line = f'- [ ] [{prefix} {num}. {rest}](https://leetcode.cn/problems/{slug}/)\n'
                new_lines.append(new_line)
                count += 1
                continue

        # Pattern: "面试题 05\.01\. 题名"
        m = re.match(PAT_IV, content)
        if m:
            n1, n2, rest = m.group(1), m.group(2), m.group(3)
            ident = f"面试题{n1}{n2}"
            slug_map_iv = {"面试题0501": "insert-into-bits-lcci"}
            slug = slug_map_iv.get(ident)
            if slug:
                new_line = f'- [ ] [面试题 {n1}.{n2}. {rest}](https://leetcode.cn/problems/{slug}/)\n'
                new_lines.append(new_line)
                count += 1
                continue

        new_lines.append(line)

    if count > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
    print(f'{fname[:55]}: {count}')
    total += count

print(f'\nTotal: {total}')
