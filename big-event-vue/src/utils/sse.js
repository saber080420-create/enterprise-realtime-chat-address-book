// Network chunks can split both UTF-8 characters and event boundaries.
export async function consumeSse(stream, onEvent) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = false
  try {
    while (!terminal) {
      const { value, done } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let boundary
      while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
        const frame = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        let event = 'message'
        const data = []
        for (const line of frame.split(/\r?\n/)) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
        }
        if (!data.length) continue
        onEvent(event, JSON.parse(data.join('\n')))
        if (event === 'done' || event === 'error') { terminal = true; break }
      }
      if (buffer.length > 131072) throw new Error('响应分片过大')
      if (done) break
    }
    if (!terminal) throw new Error('连接提前结束，回答可能不完整，请重试')
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
