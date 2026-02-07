# Ask LLM on your old brick phone!
![photo of nokia 5220 xpress music running tinyllm application with the response from ChatGPT "Hello i'm chatgpt an AI language model..."](https://i.issei.space/x4Vsov8s.jpg)

This is a repository for small silly project - using ChatGPT on old Nokia phone.

Learn more: [ChatGPT on a feature phone](https://issei.space/blog/chatgpt-on-a-featurephone/)

## How to build

1. You need to setup LiteLLM server (and have provider configured, i'm using [openrouter.ai](https://openrouter.ai), but you can use any compatible) that is exposed to the internet using pure HTTP, then set `proxyUrl` and `proxyKey` in the code. 
```java
// Default configuration - update these for your setup
private String proxyUrl = "http://litellm.issei.space:4000";
private String proxyKey = "sk-xxxxx";
private String currentModel = "openrouter/qwen/qwen3-30b-a3b";
```
Not sure if there is a good way to use env file during build time or other mean. PRs are welcome

2. As setuping a legacy java toolkits on modern operating systems is becoming harder, i prepared a [Docker container](https://github.com/mgruszkiewicz/docker-j2me-build) that contains all the necessary tools to build this project.

To build this project you will need Docker installed, and optimially if you are not running on x86 system a qemu emulator, as the container only runs on x86 - Docker Desktop and Orbstack have it by default.  
Just run in terminal
```bash
make docker-build
```

and in a second you should have a `dist` directory containing `jar` and `jad` file!
