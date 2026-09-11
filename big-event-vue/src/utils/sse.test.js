import { test } from 'node:test'
import assert from 'node:assert/strict'
import { consumeSse } from './sse.js'

function stream(text, size = 1) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream({ start(controller) {
    for (let i = 0; i < bytes.length; i += size) controller.enqueue(bytes.slice(i, i + size))
    controller.close()
  } })
}
test('UTF-8 Chinese and CRLF events split at every byte', async () => {
  const events = []
  await consumeSse(stream(': ping\r\n\r\nevent:delta\r\ndata:{"text":"你好"}\r\n\r\nevent:done\ndata:{}\n\n'), (event, data) => events.push([event, data]))
  assert.deepEqual(events, [['delta', { text: '你好' }], ['done', {}]])
})
test('multiple events in one network chunk', async () => {
  const events = []
  await consumeSse(stream('event:delta\ndata:{"text":"a"}\n\nevent:done\ndata:{}\n\n', 1000), event => events.push(event))
  assert.deepEqual(events, ['delta', 'done'])
})
test('premature EOF is not success', async () => {
  await assert.rejects(consumeSse(stream('event:delta\ndata:{"text":"a"}\n\n'), () => {}), /连接提前结束/)
})
test('server error event propagates and cancels reader', async () => {
  await assert.rejects(consumeSse(stream('event:error\ndata:{"message":"失败"}\n\n'), (_, data) => { throw new Error(data.message) }), /失败/)
})
