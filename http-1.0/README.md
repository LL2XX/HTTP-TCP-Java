# README

### ServerThread.java

命令格式：`java ServerThread <servername> -config <config_file_name>`

config_file_name表示配置文件路径，servername需在配置文件中有对应服务器名，默认为wkw.httptest.com

示例：`java ServerThread wkw.httptest.com -config httpd.conf`

### ClientThread.java

命令格式：`java ClientThread -server <server> -servname <server name> -port <server port> -parallel <# of threads> -files <file name> -T <time of test in seconds>`

server表示服务器IP，server port表示服务器开放端口，# of threads为客户端用于测试的线程数，file name为包含请求文件名列表的文件，time of test in seconds为测试时间

示例：`java ClientThread -server 192.168.0.150 -servername wkw.httptest.com -port 14382 -parallel 20 -files requests.txt -T 80`

**注意：客户端未对参数进行判断，参数格式需严格按照示例**
