import os
from datetime import date

def main():
    print("going to create the version.yamls for the respective harvester loadouts of v1.1 head and master...")
    today = date.today()
    paths =  [ './harvester-master-amd64.sha512', './harvester-v1.1-amd64.sha512' ]
    for path in paths:
        if os.path.isfile(path):
            print(f'{path} exists')
            if path == './harvester-master-amd64.sha512':
                print('creating version.yaml for master...')
                sha512 = ''
                with open(path, 'r') as file:
                    sha512 = file.readlines()[0].split(' ')[0]
                    print(f'data is {sha512}')
                with open('version-master.yaml', 'w') as version:
                    # write a newline to the file
                    version.write('apiVersion: harvesterhci.io/v1beta1\n')
                    version.write('kind: Version\n')
                    version.write('metadata:\n')
                    version.write('  name: master-head\n')
                    version.write('  namespace: harvester-system\n')
                    version.write('spec:\n')
                    version.write('  isoChecksum: ' + sha512+'\n')
                    version.write('  isoURL: http://192.168.2.34:7000/harvester-master-amd64.iso\n')
                    version.write('  releaseDate: ' + today.strftime("%Y%m%d") + '\n')
            elif path == './harvester-v1.1-amd64.sha512':
                print('creating version.yaml for v1.1...')
                sha512 = ''
                with open(path, 'r') as file:
                    sha512 = file.readlines()[0].split(' ')[0]
                    print(f'data is {sha512}')
                with open('version-v1.1.yaml', 'w') as version:
                    version.write('apiVersion: harvesterhci.io/v1beta1\n')
                    version.write('kind: Version\n')
                    version.write('metadata:\n')
                    version.write('  name: v1.1.0\n')
                    version.write('  namespace: harvester-system\n')
                    version.write('spec:\n')
                    version.write('  isoChecksum: ' + sha512 + '\n')
                    version.write('  isoURL: http://192.168.2.34:7000/harvester-v1.1-amd64.iso' + '\n')
                    version.write('  releaseDate: ' + today.strftime("%Y%m%d") + '\n')
        else:
            print(f'{path} does not exist')

if __name__ == "__main__":
    main()
