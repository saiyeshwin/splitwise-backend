/* ================================================================
   Splitwise — SPA Application Logic
   Connects to the Spring Boot backend REST API.
================================================================ */

// ── Config ──────────────────────────────────────────────────────
const API_BASE = '';   // same origin (served from Spring Boot)

// ── State ────────────────────────────────────────────────────────
let state = {
  token:          null,
  currentUser:    null,        // { id, name, email }
  groups:         [],
  selectedGroup:  null,        // full group object
  members:        [],          // GroupMember list for selected group
  balances:       [],          // BalanceResponseDTO list
  splitType:      'EQUAL',
  pendingMembers: [],          // emails staged in New Group modal
};

// ── Bootstrap ────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const saved = sessionStorage.getItem('sw_token');
  const savedUser = sessionStorage.getItem('sw_user');
  if (saved && savedUser) {
    state.token = saved;
    state.currentUser = JSON.parse(savedUser);
    showApp();
    loadGroups();
  }
});

// ─────────────────────────────────────────────────────────────────
// API CLIENT
// ─────────────────────────────────────────────────────────────────
async function api(method, path, body) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (state.token) opts.headers['Authorization'] = 'Bearer ' + state.token;
  if (body !== undefined) opts.body = JSON.stringify(body);

  const res = await fetch(API_BASE + path, opts);

  if (res.status === 401 || res.status === 403) {
    handleLogout();
    throw new Error('Session expired. Please sign in again.');
  }

  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }

  if (!res.ok) {
    const msg = (typeof data === 'object' && data.message) ? data.message
              : (typeof data === 'string' && data)          ? data
              : `Error ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

// ─────────────────────────────────────────────────────────────────
// AUTH
// ─────────────────────────────────────────────────────────────────
function switchAuthTab(tab) {
  ['login', 'register'].forEach(t => {
    document.getElementById('panel-' + t).classList.toggle('active', t === tab);
    document.getElementById('tab-'   + t).classList.toggle('active', t === tab);
  });
}

async function handleLogin() {
  const email    = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;
  const errEl    = document.getElementById('login-error');
  errEl.classList.remove('visible');

  if (!email || !password) { showError(errEl, 'Please fill in all fields.'); return; }

  const btn = document.getElementById('btn-login');
  setLoading(btn, true);
  try {
    const res = await api('POST', '/auth/login', { email, password });
    state.token = res.token;

    // Resolve current user by email from the users endpoint
    const users = await api('GET', '/users');
    state.currentUser = users.find(u => u.email === email) || { name: email, email };

    persistSession();
    showApp();
    await loadGroups();
    toast('Welcome back, ' + (state.currentUser.name || email) + '!', 'success');
  } catch (e) {
    showError(errEl, e.message || 'Invalid credentials.');
  } finally {
    setLoading(btn, false, 'Sign In');
  }
}

async function handleRegister() {
  const name     = document.getElementById('reg-name').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  const errEl    = document.getElementById('register-error');
  errEl.classList.remove('visible');

  if (!name || !email || !password) { showError(errEl, 'Please fill in all fields.'); return; }
  if (password.length < 6)          { showError(errEl, 'Password must be at least 6 characters.'); return; }

  const btn = document.getElementById('btn-register');
  setLoading(btn, true);
  try {
    await api('POST', '/auth/register', { name, email, password });
    // Auto-login after registration
    document.getElementById('login-email').value    = email;
    document.getElementById('login-password').value = password;
    switchAuthTab('login');
    toast('Account created! Please sign in.', 'success');
  } catch (e) {
    showError(errEl, e.message || 'Registration failed.');
  } finally {
    setLoading(btn, false, 'Create Account');
  }
}

function handleLogout() {
  state = { token: null, currentUser: null, groups: [], selectedGroup: null, members: [], balances: [], splitType: 'EQUAL', pendingMembers: [] };
  sessionStorage.removeItem('sw_token');
  sessionStorage.removeItem('sw_user');
  document.getElementById('app-screen').classList.remove('active');
  document.getElementById('auth-screen').style.display = 'flex';
  document.getElementById('login-email').value = '';
  document.getElementById('login-password').value = '';
}

function showApp() {
  document.getElementById('auth-screen').style.display = 'none';
  document.getElementById('app-screen').classList.add('active');
  const u = state.currentUser;
  if (u) {
    const initials = (u.name || u.email).split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
    document.getElementById('user-avatar-initials').textContent = initials;
    document.getElementById('sidebar-user-name').textContent    = u.name  || 'User';
    document.getElementById('sidebar-user-email').textContent   = u.email || '';
  }
}

function persistSession() {
  sessionStorage.setItem('sw_token', state.token);
  sessionStorage.setItem('sw_user', JSON.stringify(state.currentUser));
}

// ─────────────────────────────────────────────────────────────────
// GROUPS
// ─────────────────────────────────────────────────────────────────
async function loadGroups() {
  try {
    state.groups = await api('GET', '/groups');
    renderGroupList();
  } catch (e) {
    toast('Could not load groups: ' + e.message, 'error');
  }
}

function renderGroupList() {
  const container = document.getElementById('groups-list');
  container.innerHTML = '';

  if (!state.groups || state.groups.length === 0) {
    container.innerHTML = `<div style="padding:16px 12px;font-size:0.8rem;color:var(--text-muted);text-align:center">
      No groups yet.<br>Create one to get started.
    </div>`;
    return;
  }

  state.groups.forEach(g => {
    const div = document.createElement('div');
    div.className = 'group-item' + (state.selectedGroup?.id === g.id ? ' active' : '');
    div.dataset.id = g.id;
    const emoji = groupEmoji(g.name);
    div.innerHTML = `
      <div class="group-item-icon">${emoji}</div>
      <span class="group-item-name">${esc(g.name)}</span>
    `;
    div.onclick = () => selectGroup(g);
    container.appendChild(div);
  });
}

function groupEmoji(name) {
  const n = (name || '').toLowerCase();
  if (n.includes('trip') || n.includes('travel')) return '✈️';
  if (n.includes('flat') || n.includes('room'))   return '🏠';
  if (n.includes('food') || n.includes('dinner')) return '🍕';
  if (n.includes('party'))                        return '🎉';
  return '👥';
}

async function selectGroup(g) {
  state.selectedGroup = g;
  renderGroupList();

  document.getElementById('topbar-group-name').textContent = g.name;
  document.getElementById('topbar-group-sub').textContent  = 'Loading...';
  document.getElementById('topbar-actions').style.display = 'flex';
  document.getElementById('empty-state').style.display = 'none';
  document.getElementById('stage-content').classList.add('active');

  await Promise.all([loadMembers(g.id), loadBalances(g.id)]);
  document.getElementById('topbar-group-sub').textContent = `${state.members.length} member${state.members.length !== 1 ? 's' : ''}`;
}

// ─────────────────────────────────────────────────────────────────
// MEMBERS
// ─────────────────────────────────────────────────────────────────
async function loadMembers(groupId) {
  try {
    state.members = await api('GET', `/groups/${groupId}/members`);
    renderMembers();
  } catch (e) {
    toast('Could not load members: ' + e.message, 'error');
  }
}

function renderMembers() {
  const row = document.getElementById('members-row');
  row.innerHTML = '';
  document.getElementById('stat-members').textContent = state.members.length;

  state.members.forEach(m => {
    const displayName = m.userName || m.userEmail || '?';
    const inits = displayName.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
    const chip = document.createElement('div');
    chip.className = 'member-chip';
    chip.innerHTML = `<div class="chip-avatar">${inits}</div>${esc(displayName)}`;
    row.appendChild(chip);
  });
}

function openAddMemberModal() {
  document.getElementById('add-member-subtitle').textContent = `Add a member to "${state.selectedGroup?.name}"`;
  document.getElementById('new-member-email').value = '';
  document.getElementById('add-member-error').classList.remove('visible');
  openModal('modal-add-member');
}

async function handleAddMember() {
  const email = document.getElementById('new-member-email').value.trim();
  const errEl = document.getElementById('add-member-error');
  errEl.classList.remove('visible');
  if (!email) { showError(errEl, 'Please enter an email address.'); return; }

  try {
    // Resolve user by email
    const users  = await api('GET', '/users');
    const user   = users.find(u => u.email === email);
    if (!user) { showError(errEl, 'No user found with that email.'); return; }

    await api('POST', `/groups/${state.selectedGroup.id}/members`, { userId: user.id });
    toast(`${user.name || email} added to the group!`, 'success');
    closeModal('modal-add-member');
    await loadMembers(state.selectedGroup.id);
    document.getElementById('topbar-group-sub').textContent =
      `${state.members.length} member${state.members.length !== 1 ? 's' : ''}`;
  } catch (e) {
    showError(errEl, e.message);
  }
}

// ─────────────────────────────────────────────────────────────────
// BALANCES
// ─────────────────────────────────────────────────────────────────
async function loadBalances(groupId) {
  try {
    state.balances = await api('GET', `/groups/${groupId}/balances`);
    renderBalances();
  } catch (e) {
    toast('Could not load balances: ' + e.message, 'error');
  }
}

function renderBalances() {
  const list = document.getElementById('balances-list');
  list.innerHTML = '';

  // Compute owed/owing for current user
  let owed = 0, owing = 0;
  const myId = state.currentUser?.id;

  state.balances.forEach(b => {
    if (b.creditorId === myId) owed  += Number(b.amount);
    if (b.debtorId   === myId) owing += Number(b.amount);
  });

  document.getElementById('stat-owed').textContent  = '₹' + owed.toFixed(2);
  document.getElementById('stat-owing').textContent = '₹' + owing.toFixed(2);

  if (state.balances.length === 0) {
    list.innerHTML = `
      <div class="no-debts glass">
        <div class="nd-icon">✅</div>
        <p>All settled up! No outstanding debts in this group.</p>
      </div>`;
    return;
  }

  state.balances.forEach(b => {
    const debtorInitials   = initials(b.debtorName);
    const creditorInitials = initials(b.creditorName);
    const card = document.createElement('div');
    card.className = 'balance-card glass';
    card.innerHTML = `
      <div class="balance-avatars">
        <div class="balance-avatar debtor">${debtorInitials}</div>
        <div class="balance-avatar creditor">${creditorInitials}</div>
      </div>
      <div class="balance-info">
        <div class="balance-names">
          ${esc(b.debtorName)} <span class="arrow">→</span> ${esc(b.creditorName)}
        </div>
        <div class="balance-desc">Outstanding debt</div>
      </div>
      <div class="balance-amount">₹${Number(b.amount).toFixed(2)}</div>
    `;
    list.appendChild(card);
  });
}

// ─────────────────────────────────────────────────────────────────
// NEW GROUP MODAL
// ─────────────────────────────────────────────────────────────────
function addPendingMember() {
  const input = document.getElementById('grp-member-email');
  const email = input.value.trim();
  if (!email || !email.includes('@')) return;
  if (state.pendingMembers.includes(email)) { input.value = ''; return; }
  state.pendingMembers.push(email);
  renderPendingMembers();
  input.value = '';
}

function removePendingMember(email) {
  state.pendingMembers = state.pendingMembers.filter(e => e !== email);
  renderPendingMembers();
}

function renderPendingMembers() {
  const container = document.getElementById('pending-members');
  container.innerHTML = '';
  state.pendingMembers.forEach(email => {
    const chip = document.createElement('div');
    chip.className = 'pending-chip';
    chip.innerHTML = `${esc(email)} <span class="rm" onclick="removePendingMember('${esc(email)}')">✕</span>`;
    container.appendChild(chip);
  });
}

async function handleCreateGroup() {
  const name  = document.getElementById('grp-name').value.trim();
  const errEl = document.getElementById('new-group-error');
  errEl.classList.remove('visible');

  if (!name) { showError(errEl, 'Please enter a group name.'); return; }

  const btn = document.getElementById('btn-create-group');
  setLoading(btn, true);
  try {
    const group = await api('POST', '/groups', {
      name,
      createdById: state.currentUser.id,
    });

    // Add creator as member
    await api('POST', `/groups/${group.id}/members`, { userId: state.currentUser.id });

    // Add pending members
    const allUsers = await api('GET', '/users');
    for (const email of state.pendingMembers) {
      const user = allUsers.find(u => u.email === email);
      if (user) {
        await api('POST', `/groups/${group.id}/members`, { userId: user.id }).catch(() => {});
      }
    }

    toast(`Group "${name}" created!`, 'success');
    state.pendingMembers = [];
    document.getElementById('grp-name').value = '';
    document.getElementById('pending-members').innerHTML = '';
    closeModal('modal-new-group');
    await loadGroups();
    const freshGroup = state.groups.find(g => g.id === group.id) || group;
    selectGroup(freshGroup);
  } catch (e) {
    showError(errEl, e.message);
  } finally {
    setLoading(btn, false, 'Create Group');
  }
}

// ─────────────────────────────────────────────────────────────────
// ADD EXPENSE MODAL
// ─────────────────────────────────────────────────────────────────
function openExpenseModal() {
  // Reset
  document.getElementById('exp-desc').value   = '';
  document.getElementById('exp-amount').value = '';
  document.getElementById('expense-error').classList.remove('visible');
  state.splitType = 'EQUAL';
  setSplitType('EQUAL');

  // Populate Paid By
  const select = document.getElementById('exp-paidby');
  select.innerHTML = '';
  state.members.forEach(m => {
    const opt = document.createElement('option');
    opt.value       = m.userId;
    opt.textContent = m.userName || m.userEmail;
    if (m.userId === state.currentUser?.id) opt.selected = true;
    select.appendChild(opt);
  });

  openModal('modal-expense');
}

function setSplitType(type) {
  state.splitType = type;
  ['EQUAL', 'EXACT', 'PERCENT'].forEach(t => {
    document.getElementById('st-' + t.toLowerCase()).classList.toggle('active', t === type);
  });

  const equalInfo    = document.getElementById('split-equal-info');
  const inputsWrap   = document.getElementById('split-inputs-container');

  if (type === 'EQUAL') {
    equalInfo.style.display  = 'block';
    inputsWrap.style.display = 'none';
  } else {
    equalInfo.style.display  = 'none';
    inputsWrap.style.display = 'block';
    renderSplitInputs(type);
  }
  updateSplitHint();
}

function renderSplitInputs(type) {
  const list = document.getElementById('split-members-list');
  list.innerHTML = '';
  state.members.forEach(m => {
    const row  = document.createElement('div');
    row.className = 'split-member-row';
    const displayName = m.userName || m.userEmail || '?';
    const initl = initials(displayName);
    const label = type === 'PERCENT' ? '%' : '₹';
    row.innerHTML = `
      <div class="split-member-name">
        <div class="chip-avatar">${initl}</div>
        ${esc(displayName)}
      </div>
      <input class="split-input" type="number" min="0" step="0.01"
             id="split-${m.userId}" data-member="${m.userId}"
             placeholder="0" oninput="updateSplitHint()">
      <span class="split-input-label">${label}</span>
    `;
    list.appendChild(row);
  });
}

function getSplitValues() {
  return state.members.map(m => {
    const el = document.getElementById('split-' + m.userId);
    return { userId: m.userId, value: el ? parseFloat(el.value) || 0 : 0 };
  });
}

function updateSplitHint() {
  const validEl = document.getElementById('split-validation');
  if (state.splitType === 'EQUAL') { validEl.className = 'split-validation'; return; }

  const total  = parseFloat(document.getElementById('exp-amount').value) || 0;
  const splits = getSplitValues();
  const sum    = splits.reduce((a, b) => a + b.value, 0);

  if (state.splitType === 'EXACT') {
    if (Math.abs(sum - total) < 0.01) {
      validEl.className = 'split-validation ok';
      validEl.textContent = `✓ Splits total ₹${sum.toFixed(2)} — matches amount.`;
    } else {
      validEl.className = 'split-validation error';
      validEl.textContent = `Splits total ₹${sum.toFixed(2)} but amount is ₹${total.toFixed(2)}.`;
    }
  } else {
    if (Math.abs(sum - 100) < 0.01) {
      validEl.className = 'split-validation ok';
      validEl.textContent = `✓ Percentages total 100%.`;
    } else {
      validEl.className = 'split-validation error';
      validEl.textContent = `Percentages total ${sum.toFixed(1)}% — must equal 100%.`;
    }
  }
}

async function handleAddExpense() {
  const desc   = document.getElementById('exp-desc').value.trim();
  const amount = parseFloat(document.getElementById('exp-amount').value);
  const paidBy = parseInt(document.getElementById('exp-paidby').value, 10);
  const errEl  = document.getElementById('expense-error');
  errEl.classList.remove('visible');

  if (!amount || amount <= 0) { showError(errEl, 'Please enter a valid amount.'); return; }

  // Validate splits
  let splits = null;
  if (state.splitType !== 'EQUAL') {
    const vals = getSplitValues();
    const sum  = vals.reduce((a, b) => a + b.value, 0);
    if (state.splitType === 'EXACT'   && Math.abs(sum - amount) > 0.01) {
      showError(errEl, `Split amounts must total ₹${amount.toFixed(2)}. Currently ₹${sum.toFixed(2)}.`); return;
    }
    if (state.splitType === 'PERCENT' && Math.abs(sum - 100) > 0.01) {
      showError(errEl, `Percentages must total 100%. Currently ${sum.toFixed(1)}%.`); return;
    }
    splits = vals.filter(v => v.value > 0);
  }

  const btn = document.getElementById('btn-add-expense');
  setLoading(btn, true);
  try {
    const body = {
      description: desc || undefined,
      amount,
      paidByUserId: paidBy,
      groupId:      state.selectedGroup.id,
      splitType:    state.splitType,
    };
    if (splits) body.splits = splits.map(s => ({ userId: s.userId, value: s.value }));

    await api('POST', '/expenses', body);
    toast('Expense added!', 'success');
    closeModal('modal-expense');
    await loadBalances(state.selectedGroup.id);
  } catch (e) {
    showError(errEl, e.message);
  } finally {
    setLoading(btn, false, 'Add Expense');
  }
}

// ─────────────────────────────────────────────────────────────────
// SETTLE UP MODAL
// ─────────────────────────────────────────────────────────────────
function openSettleModal() {
  document.getElementById('settle-error').classList.remove('visible');
  document.getElementById('settle-amount').value = '';
  document.getElementById('settle-hint').style.display = 'none';

  const fromSel = document.getElementById('settle-from');
  const toSel   = document.getElementById('settle-to');
  fromSel.innerHTML = '';
  toSel.innerHTML   = '';

  state.members.forEach(m => {
    [fromSel, toSel].forEach(sel => {
      const opt = document.createElement('option');
      opt.value = m.userId;
      opt.textContent = m.userName || m.userEmail;
      sel.appendChild(opt);
    });
  });

  // Prefill from balances if possible
  if (state.balances.length > 0) {
    const b = state.balances[0];
    fromSel.value = b.debtorId;
    toSel.value   = b.creditorId;
    document.getElementById('settle-amount').value = Number(b.amount).toFixed(2);
    updateSettleHint();
  }

  openModal('modal-settle');
}

function updateSettleHint() {
  const fromId = parseInt(document.getElementById('settle-from').value, 10);
  const toId   = parseInt(document.getElementById('settle-to').value,   10);
  const hintEl = document.getElementById('settle-hint');

  const match = state.balances.find(b => b.debtorId === fromId && b.creditorId === toId);
  if (match) {
    hintEl.style.display = 'block';
    hintEl.textContent   = `💡 Outstanding debt: ₹${Number(match.amount).toFixed(2)}`;
    document.getElementById('settle-amount').value = Number(match.amount).toFixed(2);
  } else {
    hintEl.style.display = 'none';
  }
}

async function handleSettle() {
  const fromId = parseInt(document.getElementById('settle-from').value, 10);
  const toId   = parseInt(document.getElementById('settle-to').value,   10);
  const amount = parseFloat(document.getElementById('settle-amount').value);
  const errEl  = document.getElementById('settle-error');
  errEl.classList.remove('visible');

  if (fromId === toId) { showError(errEl, 'Payer and recipient must be different.'); return; }
  if (!amount || amount <= 0) { showError(errEl, 'Please enter a valid amount.'); return; }

  try {
    await api('POST', '/settlements', {
      groupId:    state.selectedGroup.id,
      fromUserId: fromId,
      toUserId:   toId,
      amount,
    });
    toast('Settlement recorded!', 'success');
    closeModal('modal-settle');
    await loadBalances(state.selectedGroup.id);
  } catch (e) {
    showError(errEl, e.message);
  }
}

// ─────────────────────────────────────────────────────────────────
// MODAL HELPERS
// ─────────────────────────────────────────────────────────────────
function openModal(id) {
  // Special pre-open hooks
  if (id === 'modal-expense') { openExpenseModal(); return; }
  if (id === 'modal-settle')  { openSettleModal();  return; }
  if (id === 'modal-new-group') { state.pendingMembers = []; document.getElementById('pending-members').innerHTML = ''; }

  document.getElementById(id).classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

// Close modal on backdrop click
document.querySelectorAll('.modal-overlay').forEach(el => {
  el.addEventListener('click', e => {
    if (e.target === el) el.classList.remove('active');
  });
});

// ─────────────────────────────────────────────────────────────────
// TOAST
// ─────────────────────────────────────────────────────────────────
function toast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
  el.innerHTML = `<span>${icon}</span> ${esc(message)}`;
  container.appendChild(el);
  setTimeout(() => {
    el.style.transition = 'opacity 0.35s, transform 0.35s';
    el.style.opacity = '0';
    el.style.transform = 'translateX(60px)';
    setTimeout(() => el.remove(), 380);
  }, 3500);
}

// ─────────────────────────────────────────────────────────────────
// UI UTILITIES
// ─────────────────────────────────────────────────────────────────
function showError(el, msg) {
  el.textContent = msg;
  el.classList.add('visible');
}

function setLoading(btn, loading, originalText) {
  if (loading) {
    btn.dataset.origText = btn.textContent;
    btn.innerHTML = '<span class="spinner"></span>';
    btn.disabled  = true;
  } else {
    btn.textContent = originalText || btn.dataset.origText || 'Submit';
    btn.disabled    = false;
  }
}

function esc(str) {
  const d = document.createElement('div');
  d.appendChild(document.createTextNode(String(str || '')));
  return d.innerHTML;
}

function initials(name) {
  return (name || '?').split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}

// Keyboard shortcuts
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    document.querySelectorAll('.modal-overlay.active').forEach(m => m.classList.remove('active'));
  }
});
