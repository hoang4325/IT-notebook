import os

root_dir = 'd:\\IT-notebook-app'
old_string = 'com.tien.it_notebook_app'
new_string = 'com.it_notebook_app'

for subdir, dirs, files in os.walk(root_dir):
    if '.git' in subdir:
        continue
    for file in files:
        if file.endswith(('.java', '.xml', '.kts', '.pro')):
            filepath = os.path.join(subdir, file)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                if old_string in content:
                    new_content = content.replace(old_string, new_string)
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f'Updated {filepath}')
            except Exception as e:
                print(f'Error reading {filepath}: {e}')
