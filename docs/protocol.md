# 官方 App 会话协议（逆向结果）

来源：官方 APK（com.deepseek.chat 2.9.0）字节码分析 + 公开逆向实现交叉验证。
仅用于「把会话改道到官方 API」这一个互操作目的。

## 1. 请求

```
POST https://chat.deepseek.com/api/v0/chat/completion
```

请求体字段（来自字节码里的序列化模型 `Lc6b` / `Lky` / `Lpl3`）：

| 字段 | 说明 |
| --- | --- |
| `chat_session_id` | 会话 ID |
| `parent_message_id` | 上一条消息 ID，新会话为 null |
| `prompt` | 用户输入 |
| `ref_file_ids` | 引用文件，无则 `[]` |
| `thinking_enabled` | 是否深度思考 |
| `search_enabled` | 是否联网 |
| `model_type` | `default` / `expert` / `vision` |

请求头带登录态（`Authorization: Bearer <token>`）、`x-ds-pow-response`（PoW 挑战应答，见 `create_pow_challenge` 与 `Lcom/deepseek/crypto/PowCalculator`）。

## 2. 响应：SSE，`data: {json}` 逐行

**首帧**（元数据）：

```json
{"v":{"response":{
  "message_id":"...","parent_id":"...","role":"ASSISTANT",
  "thinking_enabled":false,"status":"WIP",
  "fragments":[{"id":1,"type":"RESPONSE","content":""}],
  "conversation_mode":"DEFAULT",
  "accumulated_token_usage":0}}}
```

**内容增量**，两种写法并存：

```json
{"p":"response/fragments","o":"APPEND","v":[{"id":2,"type":"THINK","content":""}]}
{"p":"response/fragments/-1/content","o":"APPEND","v":"文字"}
```

无 `p` 的续帧（沿用上一片的类型）：

```json
{"o":"APPEND","v":"更多文字"}
{"v":"再更多"}
```

**旧格式**（部分场景仍在用）：

```json
{"p":"response/thinking_content","v":"嗯"}
{"p":"response/content","o":"APPEND","v":"你好"}
```

**结束**：

```json
{"p":"response/status","v":"FINISHED"}
```

## 3. 片段类型

`THINK`（思考）、`RESPONSE`（正文）、`SEARCH`（联网结果）、`TEMPLATE_RESPONSE`、`TOOL_SEARCH`。
操作符：`SET` / `APPEND` / `BATCH`。

## 4. 消息模型字段（完整）

`message_id`、`parent_id`、`role`、`inserted_at`、`ban_edit`、`ban_regenerate`、
`status`、`quasi_status`、`thinking_enabled`、`search_enabled`、`search_triggered`、
`accumulated_token_usage`、`extra_search_providers`、`feedback`、`tips`、
`fragments`、`has_pending_fragment`、`conversation_mode`、`incomplete_message`。

## 5. 与 OpenAI 兼容 API 的映射

| 官方 | OpenAI 兼容（api.deepseek.com） |
| --- | --- |
| `prompt` | `messages[last].content` |
| `thinking_enabled` | `thinking.type = enabled/disabled` |
| `THINK` 片段 | `delta.reasoning_content` |
| `RESPONSE` 片段 | `delta.content` |
| `accumulated_token_usage` | `usage.total_tokens` |
| `response/status=FINISHED` | `finish_reason` / `[DONE]` |

历史上下文需要自行把 `fragments` 还原成 messages 数组，再按 OpenAI 格式提交。

## 6. 待解决

官方 App 的网络栈是 Ktor + OkHttp，类名被 R8 混淆（`okhttp3.Request`、`io.ktor.client.HttpClient` 均无法按名引用），
因此「拦截并替换响应」需要一个稳定的 hook 点。当前候选：

1. 改写 BaseUrl 常量，把请求导向模块内的本地转换代理（需同时放开明文限制）
2. 定位 `chat/completion` 的调用方 `Lc6b` 的上级，hook 其响应流
3. 在 `io.ktor.client.engine.okhttp.OkHttpEngineContainer` 上取到引擎实例后反射 hook `execute`
