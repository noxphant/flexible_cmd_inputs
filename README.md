# Introduction 简介
  Flexible Command Inputs (**FCI**) is a Minecraft client-side mod whose core functionality is to provide a **WebUI/API** for convenient in-game command input. It supports port configuration, log recording and client status (FPS/Ping/TPS) monitoring, streamlining the command operation process.

---
  Flexible Command Inputs（**FCI**）是一款 Minecraft 客户端模组，核心提供 **WebUI/API** 便捷输入游戏命令，支持端口配置、日志记录及客户端状态（FPS/Ping/TPS）监控，简化命令操作流程。

# Translation 使用说明
Access http://localhost:[port] or http://127.0.0.1:[port] to enter the WebUI.

---

  访问http://localhost:[端口]或http://127.0.0.1:[端口]即可进入webui。
# Commands | 指令
The mod provides the following commands:
```
/fci getport
```
Get the current port number of the WebUI/API (the default port number is 8080)
```
/fci setport [port]
```

Change the current port number of the WebUI/API to [port] (valid range: 1–65535)

---
  模组提供了以下命令：
  
```
/fci getport
```
获取当前webui/api的端口号（默认端口号为8080）
  
```
/fci setport [port]
```
修改当前webui/api的端口号为port（1-65535）

# API Interface | API接口

1. Request Method: POST Request
2. Access Address:
```
http://localhost:[port]/api/cmd?command=[command]
```
[command] refers to the Minecraft command to be executed.
[port] must be consistent with the current port number of the mod (you can check it with /fci getport)
Return Format: JSON (the execution result is returned directly)

---
1. 请求方式：POST 请求
2. 访问地址：
   
```
http://localhost:[port]/api/cmd?command=[command]
```
   [command] 为待执行的 Minecraft 命令
   [port] 为端口需与模组当前端口一致（可使用 _/fci getport_ 查看）
返回格式：JSON（直接返回执行结果）
