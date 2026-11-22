# 官方 App 会话协议（真实抓包确认）

来源：官方 APK（com.deepseek.chat 2.9.0）字节码分析 + **真机登录后的 TLS 明文抓包**（2026-11-23）。
仅用于「把会话改道到官方 API」这一互操作目的。

## 1. 请求

```
POST https://chat.deepseek.com/api/v0/chat/completion
```

请求体（来自字节码模型 + 抓包）：

```json
{
  "chat_session_id": "d07d08dd-c642-4ed3-834c-9f6eedda1e32",
  "parent_message_id": null,
  "prompt": "hi",
  "ref_file_ids": [],
  "thinking_enabled": true,
  "search_enabled": true,
  "model_type": "default"
}
```

新建会话时先调 `/api/v0/chat_session/create`，返回：

```json
{"code":0,"msg":"","data":{"biz_code":0,"biz_msg":"","biz_data":{
  "chat_session":{"id":"...","title":null,"title_type":"WIP","model_type":"default",
                  "pinned":false,"updated_at":1795364595.563,"seq_id":217527795,
                  "agent":"chat","version":0,"is_empty":true,
                  "current_message_id":null,"inserted_at":1795364595.563},
  "ttl_seconds":259200}}}
```

鉴权：`Authorization: Bearer <token>` + `x-ds-pow-response`（PoW 挑战，见 `/api/v0/chat/create_pow_challenge` 与 `Lcom/deepseek/crypto/PowCalculator`）。

## 2. 响应：SSE，逐行 `data: {json}`

**首帧**（真实抓包，逐字）：

```json
{"v":{"response":{
  "message_id":2,"parent_id":1,"model":"","role":"ASSISTANT",
  "thinking_enabled":true,"ban_edit":false,"ban_regenerate":false,
  "status":"WIP","incomplete_message":null,"accumulated_token_usage":0,
  "feedback":null,"inserted_at":1795364595.833729,"search_enabled":true,
  "fragments":[{"id":2,"type":"THINK","content":"The",
                "elapsed_secs":null,"references":[],"stage_id":1}],
  "conversation_mode":"DEFAULT","has_pending_fragment":false,
  "auto_continue":false,"search_triggered":false,
  "extra_search_providers":[]}}}
```

**增量**，三种写法都会出现：

```json
{"p":"response/fragments/-1/content","o":"APPEND","v":" user"}   // 带路径
{"v":"hi"}                                                        // 只有 v，续写上一片
{"o":"APPEND","v":"，" }                                          // 只有 o+v
```

**其他帧**：

```json
{"request_message_id":1,"response_message_id":2,"model_type":"default"}
{"updated_at":1795364595.8471138}
{"p":"response/status","v":"FINISHED"}
```

## 3. 片段与状态

片段类型：`THINK`（思考）、`RESPONSE`（正文）、`SEARCH`（联网）、`TEMPLATE_RESPONSE`、`TOOL_SEARCH`。
片段字段：`id`、`type`、`content`、`elapsed_secs`、`references`、`stage_id`。
操作符：`SET` / `APPEND` / `BATCH`。

消息状态：`WIP`（生成中）→ `FINISHED`。

## 4. 消息模型字段（完整）

`message_id`、`parent_id`、`model`、`role`、`inserted_at`、`ban_edit`、`ban_regenerate`、
`status`、`quasi_status`、`thinking_enabled`、`search_enabled`、`search_triggered`、
`accumulated_token_usage`、`extra_search_providers`、`feedback`、`tips`、
`fragments`、`has_pending_fragment`、`conversation_mode`、`incomplete_message`、`auto_continue`。

## 5. 与 OpenAI 兼容 API 的映射

| 官方 | api.deepseek.com |
| --- | --- |
| `prompt` | `messages[last].content` |
| `thinking_enabled` | `thinking.type = enabled/disabled` |
| `THINK` 片段 | `delta.reasoning_content` |
| `RESPONSE` 片段 | `delta.content` |
| `accumulated_token_usage` | `usage.total_tokens` |
| `response/status = FINISHED` | `finish_reason` |

历史上下文需自行把 `fragments` 还原成 messages 数组，再按 OpenAI 格式提交。

## 6. 注入点（已验证可行）

官方 App 的网络栈 Ktor + OkHttp **类名被 R8 全量混淆**，无法按类名 hook。

但 **TLS 解密流可以钩**：

```
com.android.org.conscrypt.ConscryptEngineSocket#getInputStream
com.android.org.conscrypt.OpenSSLSocketImpl#getInputStream
```

钩住后拿到的是**解密后的明文流**，已经用它抓到了上面的 SSE。同样的位置可以**替换**返回的数据流——
把官方请求的响应换成「调用 api.deepseek.com 后按官方格式重新编码」的内容，官方 UI 无需任何改动。

模块里对应实现：`TapStream`（读并记录）+ `SseTranslator`（OpenAI 格式 → 官方格式）。
