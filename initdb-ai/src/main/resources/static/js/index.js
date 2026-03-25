// 页面交互逻辑（从 index.html 内联脚本迁移而来）
/* global Api */

const Api = window.Api;

// 用于存储所有会话的数组
let sessions = JSON.parse(localStorage.getItem('dbSessions')) || [];
let currentSessionId = null;

function saveSessions() {
  localStorage.setItem('dbSessions', JSON.stringify(sessions));
}

// 渲染会话列表
function renderSessionList() {
  const sessionListElement = document.getElementById('sessionList');
  sessionListElement.innerHTML = '';

  sessions.forEach(session => {
    const div = document.createElement('div');
    div.className = `session-item ${session.id === currentSessionId ? 'active' : ''}`;
    div.textContent = session.name;
    div.onclick = () => switchSession(session.id);
    sessionListElement.appendChild(div);
  });
}

// 打开模态框
function createModal() {
  document.getElementById('sessionModal').style.display = 'flex';
}

async function deleteModal() {
  try {
    await Api.del('/chat/delete/all');
  } catch (error) {
    console.log(error);
  }

  sessions = [];
  localStorage.clear();
  renderSessionList();
  document.getElementById('chatContainer').style.display = 'none';
  document.getElementById('welcomeScreen').style.display = 'flex';
}

let index = 1;

// 关闭创建新会话框
function closeModal() {
  document.getElementById('sessionModal').style.display = 'none';

  // 清空表单并重置为默认值
  document.getElementById('sessionName').value = '新的对话' + (index++);
  document.getElementById('url').value = 'jdbc:mysql://127.0.0.1:3306/xxx_xxx_xxx?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000';
  document.getElementById('username').value = 'xxx';
  document.getElementById('password').value = '';
}

// 创建新会话
async function createNewSession() {
  const sessionName = document.getElementById('sessionName').value.trim();
  const url = document.getElementById('url').value.trim();
  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;

  try {
    const data = await Api.post('/chat/create', {
      sessionName,
      url,
      username,
      password
    });

    const newSessionId = data.sessionId;
    const newSession = {
      id: newSessionId,
      name: sessionName,
      config: {username, password},
      messages: []
    };

    sessions.unshift(newSession);
    saveSessions();
    renderSessionList();
    switchSession(newSession.id);
    closeModal();
  } catch (error) {
    console.error('Connection error:', error);
    alert(error.message || '创建会话时候发送未知错误！');
  } finally {
    // todo
  }
}

function setSendButtonDisabled(disabled) {
  const btn = document.getElementById('sendMessageBtn');
  if (btn) btn.disabled = !!disabled;
}

// 切换会话
function switchSession(sessionId) {
  currentSessionId = sessionId;
  const session = sessions.find(s => s.id === sessionId);

  if (session) {
    document.getElementById('chatContainer').style.display = 'flex';
    document.getElementById('welcomeScreen').style.display = 'none';
    document.getElementById('currentSessionHeader').textContent = session.name;
    renderChatMessages(session.messages);

    document.getElementById('userInput').disabled = false;
    setSendButtonDisabled(false);
  }

  renderSessionList();
}

// 渲染特定会话的消息
function renderChatMessages(messages) {
  const chatMessagesElement = document.getElementById('chatMessages');
  chatMessagesElement.innerHTML = '';

  messages.forEach(msg => {
    addMessageToDOM(msg.role, msg.content);
  });

  // 滚动到底部
  chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
}

// 将消息添加到DOM
function addMessageToDOM(role, content) {
  const chatMessagesElement = document.getElementById('chatMessages');
  const messageDiv = document.createElement('div');
  messageDiv.className = `message ${role}-message`;
  messageDiv.innerHTML = content;
  chatMessagesElement.appendChild(messageDiv);

  chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
}

// 发送消息
async function sendMessage() {
  const inputElement = document.getElementById('userInput');
  const message = inputElement.value.trim();

  if (!message) return;

  if (!currentSessionId) {
    alert('请先创建并选择一个对话');
    return;
  }

  const sessionIndex = sessions.findIndex(s => s.id === currentSessionId);
  if (sessionIndex === -1) return;

  // 添加用户消息
  const userMsg = {role: 'user', content: message};
  sessions[sessionIndex].messages.push(userMsg);
  addMessageToDOM('user', message);
  inputElement.value = '';

  // 禁用输入以等待响应
  inputElement.disabled = true;
  setSendButtonDisabled(true);

  try {
    const data = await Api.post('/db/chat', {
      message,
      sessionId: currentSessionId,
      db_config: sessions[sessionIndex].config
    });

    if (data && data.response) {
      const botMsg = {role: 'bot', content: data.response};
      sessions[sessionIndex].messages.push(botMsg);
      addMessageToDOM('bot', data.response);
    } else {
      throw new Error('请求失败：返回数据不符合预期');
    }
  } catch (error) {
    console.error('Error:', error);
    const errorMsg = {role: 'bot', content: `错误: ${error.message}`};
    sessions[sessionIndex].messages.push(errorMsg);
    addMessageToDOM('bot', `错误: ${error.message}`);
  } finally {
    inputElement.disabled = false;
    setSendButtonDisabled(false);
    inputElement.focus();
  }

  // 发送完消息后保存会话
  saveSessions();
}

// 回车键发送消息
function handleKeyPress(event) {
  if (event.key === 'Enter') {
    sendMessage();
  }
}

// 页面加载时初始化
window.onload = function () {
  renderSessionList();

  // 如果有会话，则默认加载第一个
  if (sessions.length > 0 && !currentSessionId) {
    switchSession(sessions[0].id);
  }
  // 否则保持欢迎界面显示
};

// 上传文件
function triggerFileInput() {
  document.getElementById('fileInput').click();
}

function resetFileSelectionUI() {
  document.getElementById('fileInput').value = '';
  window.selectedFile = null;
  document.getElementById('fileList').style.display = 'none';
  document.getElementById('fileNameText').textContent = '';

  const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
  uploadFileSendBtn.style.display = 'none';
  uploadFileSendBtn.disabled = true;
  uploadFileSendBtn.classList.remove('uploading');
  uploadFileSendBtn.setAttribute('aria-label', '上传并处理');
}

function handleFileSelect(fileInput) {
  const files = fileInput.files;
  if (files.length === 0) {
    resetFileSelectionUI();
    return;
  }

  // 严格限制为单个文件
  if (files.length > 1) {
    alert('每次只能上传一个文件，请重新选择。');
    resetFileSelectionUI();
    return;
  }

  const file = fileInput.files[0];
  if (!file) {
    resetFileSelectionUI();
    return;
  }

  // 检查文件类型是否为 .txt
  if (file.type !== 'text/plain') {
    alert('不支持的文件类型。请上传 .txt 文件。');
    resetFileSelectionUI();
    return;
  }

  window.selectedFile = file;
  document.getElementById('fileNameText').textContent = file.name;
  document.getElementById('fileList').style.display = 'block';

  const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
  uploadFileSendBtn.style.display = 'inline-flex';
  uploadFileSendBtn.disabled = false;
}

async function uploadSelectedFile() {
  if (!window.selectedFile) {
    console.warn('没有选中的文件可供上传。');
    return;
  }

  const uploadFileSendBtn = document.getElementById('uploadFileSendBtn');
  uploadFileSendBtn.disabled = true;
  uploadFileSendBtn.classList.add('uploading');
  uploadFileSendBtn.setAttribute('aria-label', '上传中...');

  const userInput = document.getElementById('userInput');
  // 检查输入框是否禁用，如果禁用则无法获取当前会话ID
  if (userInput.disabled) {
    alert('请先开启一个会话后再上传文件。');
    uploadFileSendBtn.disabled = false;
    uploadFileSendBtn.classList.remove('uploading');
    uploadFileSendBtn.setAttribute('aria-label', '上传并处理');
    return;
  }

  const formData = new FormData();
  formData.append('file', window.selectedFile);
  formData.append('sessionId', currentSessionId);

  try {
    const result = await Api.requestForm('/document/upload/txt', formData);
    console.log('文件上传成功:', result);
  } catch (error) {
    console.error('文件上传失败:', error);
  } finally {
    resetFileSelectionUI();
  }
}

