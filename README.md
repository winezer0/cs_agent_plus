# CS_Agent_INA
CS 4.5 Java Agent INA  

为了修复XSS RCE的同时提供其他功能聚合而成的cs agent

```
1、支持XSS RCE修复、汉化、agent调试限制绕过及其他功能
2、理论支持CS4.X版本，Java8|java11下CS4.5原版 已进行运行验证测试
```

## 声明

```
1、非完全原创代码, 整合于CSAgent、Attack2DefenseAgent、CVE-2022-39197 patch
2、本工具仅供学习研究、如您在使用本工具的过程中存在任何非法行为，您需自行承担相应后果，我们将不承担任何法律及连带责任。
```




## 更新记录:


cs_agent_0.0.8

```
1、新增 支持输入原始比特格式的密钥、水印hash、水印值、会输出对应的转换后的值。
2、新增 内置CS4.6 CS4.7版本的密钥 实际可能不支持
```

cs_agent_0.0.7

```
1、新增  支持翻译report按钮 及 对应配置文件参数
```
cs_agent_0.0.6

```
1、新增 内置4.0-4.5版本解密密钥，支持CS4.4及一下版本，测试CS4.4。
2、新增 配置文件CS Version相关参数,该参数影响对应的版本默认属性，默认为4.5。
3、新增 配置文件支持 Auto字符串，表示自动配置对应参数。
4、注意 对于使用的版本如果是已修改版的CS，请关闭对应功能
```
cs_agent_0.0.5

```
1、当配置文件不存在时，支持生成默认配置文件。 且配置文件不存在时，会进行跳过，即本次不进行agent劫持

2、实现所有参数通过配置文件指定。 注意：不再可以指定jar包的内的路径，且初始化时为了不影响启动，会清除实际使用的文件，再进行拷贝。
```
cs_agent_0.0.4

```
1、修复代码逻辑, 当不开启翻译功能时，可删除resources和scripts目录
```

cs_agent_0.0.2   

```
0、各功能理论完全分离、可单独进行开启关闭。
1、支持CS4.5 crack_auth  【配置need.crack_auth 可选 Y/N】
2、支持CS4.5 原版汉化  【配置need.translation 可选 Y/N】
3、支持CS4.5 XSS RCE修复  【配置need.fix_xss_rce 可选 Y/N】
4、支持CS4.5 javaagent调试限制绕过  【配置need.crack_agent 可选 Y/N】
5、支持自定义配置文件名称  【可选 -javaagent:cs_agent.jar=config.ini 】
```



## 使用方法：



一、下载项目

```
项目文件夹介绍:

resources 汉化资源文件夹,放置在agent.jar同级目录
scripts 汉化资源文件夹,放置在agent.jar同级目录
PS：不使用汉化功能时可删除以上文件夹

src 项目源码文件夹
bin 历史发布jar包文件夹
cs_agent.jar 实际使用的jar文件
cs_agent.ini cs_agent.jar的配置文件,不存在会自动生成
```



三、在启动命令行添加agent运行

```
config.ini 配置文件
不存在会在运行时自动生成
如果更新版本建议删除后重新生成

1、生成默认配置文件config.ini 
-javaagent:cs_agent.jar

2、生成自定义配置文件名称custom.ini 
-javaagent:cs_agent.jar=custom.ini

命令实例：

客户端启动（默认配置文件名称config.ini）::
java -XX:ParallelGCThreads=4 -XX:+AggressiveHeap -XX:+UseParallelGC -Xms512M -Xmx1024M -Dfile.encoding=UTF-8 -javaagent:cs_agent.jar -jar cobaltstrike.jar %*

客户端启动（自定义配置文件名称custom.ini）:
java -XX:ParallelGCThreads=4 -XX:+AggressiveHeap -XX:+UseParallelGC -Xms512M -Xmx1024M -Dfile.encoding=UTF-8 -javaagent:cs_agent.jar=custom.ini -jar cobaltstrike.jar %*

注：服务端也可以使用该agent进行漏洞修复|agent调试绕过等操作.

命令实例：（仅最后一行）：

服务端启动（默认配置文件名称config.ini）::
java -XX:ParallelGCThreads=4 -XX:+AggressiveHeap -XX:+UseParallelGC -Xms512M -Xmx1024M -Djavax.net.ssl.keyStore=./default.store -Djavax.net.ssl.keyStorePassword=123456 -Dcobaltstrike.server_port=55555 -javaagent:cs_agent.jar -server -classpath ./cobaltstrike.jar server.TeamServer %* 

服务端启动（自定义配置文件名称custom.ini）::
java -XX:ParallelGCThreads=4 -XX:+AggressiveHeap -XX:+UseParallelGC -Xms512M -Xmx1024M -Djavax.net.ssl.keyStore=./default.store -Djavax.net.ssl.keyStorePassword=123456 -Dcobaltstrike.server_port=55555 -javaagent:cs_agent.jar=custom.ini -server -classpath ./cobaltstrike.jar server.TeamServer %*
```



三、根据需求配置config.ini

```
cs_agent.ini配置说明：
attrib.cs_version=4.5  ->指定CS版本号,不同版本key不相同
beacon.sleeved.key=Auto ->Auto表示根据版本号自动获取，不同版本值不相同
beacon.watermark.hash=Auto ->Auto表示根据版本号自动获取，不同版本值不相同
beacon.watermark.value=000000 ->替换水印的值,随意应该OK

file.inner_cna_zh=scripts/default_zh.cna ->汉化default.cna
file.inner_details_zh=resources/bdetails_zh.txt ->汉化bdetails.tx
file.inner_help_zh=resources/bhelp_zh.txt ->汉化bhelp.txt
file.inner_rpt_zh=scripts/default_zh.rpt ->汉化default.txt
file.translate_zh=resources/translate_zh.txt ->汉化translate.txt

need.crack_agent=Y  ->javaagent限制绕过,Y开启，N关闭
need.crack_auth=Y  ->CS agent破解,Y开启，N关闭
need.fix_xss_rce=Y  ->修复XSS RCE,Y开启，N关闭
need.translation=N  ->汉化翻译功能,Y开启，N关闭

修改后需要重新启动cs程序
```



