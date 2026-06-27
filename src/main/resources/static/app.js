/* ================================================================
   Splitwise — SPA Application Logic
================================================================ */

const API_BASE = '';

let state = {
  token:          null,
  currentUser:    null,
  groups:         [],
  selectedGroup:  null,
  members:        [],
  balances:       [],
  splitType:      'EQUAL',
  pendingMembers: [],
};

// ─────────────────────────────────────────────────────────────────
// BOOTSTRAP
// ─────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const savedToken = sessionStorage.getItem('sw_token');
  const savedUser  = sessionStorage.getItem('sw_user');
  if (savedToken && savedUser) {
    state.token = savedToken;
    state.currentUser = JSON.parse(savedUser);
    showApp();
    loadGroups();
  }

  // Close modal on backdrop click
  document.querySelectorAll('.modal-overlay').forEach(el => {
    el.addEventListener('click', e => { if (e.target === el) el.classList.remove('active'); });
  });

  // Keyboard shortcut
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape')
      document.querySelectorAll('.modal-overlay.active').forEach(m => m.classList.remove('active'));
  });
});

// ─────────────────────────────────────────────────────────────────
// API CLIENT
// ─────────────────────────────────────────────────────────────────
async function api(method, path, body) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (state.token) opts.headers['Authorization'] = 'Bearer ' + state.token;
  if (body !== undefined) opts.body = JSON.stringify(body);

  const res  = await fetch(API_BASE + path, opts);

  if (res.status === 401 || res.status === 403) {
    handleLogout();
    throw new Error('Session expired. Please sign in again.');
  }

  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }

  if (!res.ok) {
    throw new Error(
      (typeof data === 'object' && data.message) ? data.message :
      (typeof data === 'string' && data)          ? data :
      `Error ${res.status}`
    );
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
  hideAlert(errEl);

  if (!email || !password) { showAlert(errEl, 'Please fill in all fields.'); return; }

  const btn = document.getElementById('btn-login');
  setLoading(btn, true, 'Sign In');
  try {
    const res   = await api('POST', '/auth/login', { email, password });
    state.token = res.token;

    const users = await api('GET', '/users');
    state.currentUser = users.find(u => u.email === email) || { name: email, email };

    sessionStorage.setItem('sw_token', state.token);
    sessionStorage.setItem('sw_user', JSON.stringify(state.currentUser));
    showApp();
    await loadGroups();
    toast('Signed in successfully.', 'success');
  } catch (e) {
    showAlert(errEl, e.message || 'Invalid credentials.');
  } finally {
    setLoading(btn, false, 'Sign In');
  }
}

async function handleRegister() {
  const name     = document.getElementById('reg-name').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  const errEl    = document.getElementById('register-error');
  hideAlert(errEl);

  if (!name || !email || !password) { showAlert(errEl, 'Please fill in all fields.'); return; }
  if (password.length < 6)          { showAlert(errEl, 'Password must be at least 6 characters.'); return; }

  const btn = document.getElementById('btn-register');
  setLoading(btn, true, 'Create Account');
  try {
    await api('POST', '/auth/register', { name, email, password });
    document.getElementById('login-email').value    = email;
    document.getElementById('login-password').value = password;
    switchAuthTab('login');
    toast('Account created. Please sign in.', 'success');
  } catch (e) {
    showAlert(errEl, e.message || 'Registration failed.');
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
  document.getElementById('login-email').value    = '';
  document.getElementById('login-password').value = '';
}

function showApp() {
  document.getElementById('auth-screen').style.display = 'none';
  document.getElementById('app-screen').classList.add('active');
  const u = state.currentUser;
  if (u) {
    const inits = avatarInitials(u.name || u.email);
    document.getElementById('user-avatar-initials').textContent = inits;
    document.getElementById('sidebar-user-name').textContent    = u.name  || 'User';
    document.getElementById('sidebar-user-email').textContent   = u.email || '';
  }
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
    container.innerHTML = `<div class="groups-empty">No groups yet.<br>Create one to get started.</div>`;
    return;
  }

  state.groups.forEach(g => {
    const div = document.createElement('div');
    div.className = 'group-item' + (state.selectedGroup?.id === g.id ? ' active' : '');
    div.innerHTML = `
      <svg><use href="#ic-users"/></svg>
      <span class="group-item-name">${esc(g.name)}</span>
    `;
    div.onclick = () => selectGroup(g);
    container.appendChild(div);
  });
}

async function selectGroup(g) {
  state.selectedGroup = g;
  renderGroupList();

  document.getElementById('topbar-group-name').textContent = g.name;
  document.getElementById('topbar-group-sub').textContent  = 'Loading…';
  document.getElementById('topbar-actions').style.display = 'flex';
  document.getElementById('empty-state').style.display    = 'none';
  document.getElementById('stage-content').classList.add('active');

  await Promise.all([loadMembers(g.id), loadBalances(g.id)]);
  document.getElementById('topbar-group-sub').textContent =
    `${state.members.length} member${state.members.length !== 1 ? 's' : ''}`;
}

// ─────────────────────────────────────────────────────────────────
// MEMBERS
// ─────────────────────────────────────────────────────────────────
async function loadMembers(groupId) {
  try {
    state.members = await api('GET', `/groups/${groupId}/members`);
    renderMembers();
  } catch (e) {
    toast('Could not load members.', 'error');
  }
}

function renderMembers() {
  const row = document.getElementById('members-row');
  row.innerHTML = '';
  document.getElementById('stat-members').textContent = state.members.length;

  state.members.forEach(m => {
    const displayName = m.userName || m.userEmail || '?';
    const inits = avatarInitials(displayName);
    const chip  = document.createElement('div');
    chip.className = 'member-chip';
    chip.innerHTML = `<div class="avatar avatar-sm">${inits}</div>${esc(displayName)}`;
    row.appendChild(chip);
  });
}

function openAddMemberModal() {
  document.getElementById('add-member-subtitle').textContent = `Add a member to "${state.selectedGroup?.name}"`;
  document.getElementById('new-member-email').value = '';
  hideAlert(document.getElementById('add-member-error'));
  openModal('modal-add-member');
}

async function handleAddMember() {
  const email = document.getElementById('new-member-email').value.trim();
  const errEl = document.getElementById('add-member-error');
  hideAlert(errEl);
  if (!email) { showAlert(errEl, 'Please enter an email address.'); return; }

  try {
    const users = await api('GET', '/users');
    const user  = users.find(u => u.email === email);
    if (!user) { showAlert(errEl, 'No account found with that email.'); return; }

    await api('POST', `/groups/${state.selectedGroup.id}/members`, { userId: user.id });
    toast(`${user.name || email} added to the group.`, 'success');
    closeModal('modal-add-member');
    await loadMembers(state.selectedGroup.id);
    document.getElementById('topbar-group-sub').textContent =
      `${state.members.length} member${state.members.length !== 1 ? 's' : ''}`;
  } catch (e) {
    showAlert(errEl, e.message);
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
    toast('Could not load balances.', 'error');
  }
}

function renderBalances() {
  const list = document.getElementById('balances-list');
  list.innerHTML = '';

  const myId = state.currentUser?.id;
  let owed = 0, owing = 0;
  state.balances.forEach(b => {
    if (b.creditorId === myId) owed  += Number(b.amount);
    if (b.debtorId   === myId) owing += Number(b.amount);
  });

  document.getElementById('stat-owed').textContent  = '₹' + owed.toFixed(2);
  document.getElementById('stat-owing').textContent = '₹' + owing.toFixed(2);

  if (state.balances.length === 0) {
    list.innerHTML = `
      <div class="all-settled">
        <svg><use href="#ic-check-circle"/></svg>
        <p>All settled up. No outstanding debts in this group.</p>
      </div>`;
    return;
  }

  state.balances.forEach(b => {
    const di = avatarInitials(b.debtorName);
    const ci = avatarInitials(b.creditorName);
    const row = document.createElement('div');
    row.className = 'balance-row';
    row.innerHTML = `
      <div class="balance-avatars">
        <div class="avatar avatar-md balance-avatar-debtor">${di}</div>
        <div class="avatar avatar-md balance-avatar-creditor">${ci}</div>
      </div>
      <div class="balance-info">
        <div class="balance-names">
          ${esc(b.debtorName)}
          <span class="balance-arrow"><svg><use href="#ic-arrow-right"/></svg></span>
          ${esc(b.creditorName)}
        </div>
        <div class="balance-sub">Outstanding debt</div>
      </div>
      <div class="balance-amount">₹${Number(b.amount).toFixed(2)}</div>
    `;
    list.appendChild(row);
  });
}

// ─────────────────────────────────────────────────────────────────
// NEW GROUP
// ─────────────────────────────────────────────────────────────────
function openNewGroupModal() {
  state.pendingMembers = [];
  document.getElementById('grp-name').value = '';
  document.getElementById('grp-member-email').value = '';
  document.getElementById('pending-members').innerHTML = '';
  hideAlert(document.getElementById('new-group-error'));
  openModal('modal-new-group');
}

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
  const c = document.getElementById('pending-members');
  c.innerHTML = '';
  state.pendingMembers.forEach(email => {
    const chip = document.createElement('div');
    chip.className = 'pending-chip';
    chip.innerHTML = `${esc(email)}<button class="pending-chip-rm" onclick="removePendingMember('${esc(email)}')" title="Remove">&times;</button>`;
    c.appendChild(chip);
  });
}

async function handleCreateGroup() {
  const name  = document.getElementById('grp-name').value.trim();
  const errEl = document.getElementById('new-group-error');
  hideAlert(errEl);
  if (!name) { showAlert(errEl, 'Please enter a group name.'); return; }

  const btn = document.getElementById('btn-create-group');
  setLoading(btn, true, 'Create group');
  try {
    const group = await api('POST', '/groups', { name, createdById: state.currentUser.id });
    await api('POST', `/groups/${group.id}/members`, { userId: state.currentUser.id });

    if (state.pendingMembers.length > 0) {
      const allUsers = await api('GET', '/users');
      for (const email of state.pendingMembers) {
        const user = allUsers.find(u => u.email === email);
        if (user) await api('POST', `/groups/${group.id}/members`, { userId: user.id }).catch(() => {});
      }
    }

    toast(`Group "${name}" created.`, 'success');
    closeModal('modal-new-group');
    await loadGroups();
    const fresh = state.groups.find(g => g.id === group.id) || group;
    selectGroup(fresh);
  } catch (e) {
    showAlert(errEl, e.message);
  } finally {
    setLoading(btn, false, 'Create group');
  }
}

// ─────────────────────────────────────────────────────────────────
// ADD EXPENSE
// ─────────────────────────────────────────────────────────────────
function openExpenseModal() {
  document.getElementById('exp-desc').value   = '';
  document.getElementById('exp-amount').value = '';
  hideAlert(document.getElementById('expense-error'));
  document.getElementById('split-validation').className = 'validation-msg';
  state.splitType = 'EQUAL';
  setSplitType('EQUAL');

  const sel = document.getElementById('exp-paidby');
  sel.innerHTML = '';
  state.members.forEach(m => {
    const opt = document.createElement('option');
    opt.value = m.userId;
    opt.textContent = m.userName || m.userEmail;
    if (m.userId === state.currentUser?.id) opt.selected = true;
    sel.appendChild(opt);
  });
  openModal('modal-expense');
}

function setSplitType(type) {
  state.splitType = type;
  ['EQUAL', 'EXACT', 'PERCENT'].forEach(t => {
    document.getElementById('st-' + t.toLowerCase()).classList.toggle('active', t === type);
  });

  const equalInfo  = document.getElementById('split-equal-info');
  const inputsWrap = document.getElementById('split-inputs-container');

  if (type === 'EQUAL') {
    equalInfo.style.display  = 'block';
    inputsWrap.style.display = 'none';
  } else {
    equalInfo.style.display  = 'none';
    inputsWrap.style.display = 'block';
    renderSplitRows(type);
  }
  updateSplitHint();
}

function renderSplitRows(type) {
  const list = document.getElementById('split-rows');
  list.innerHTML = '';
  state.members.forEach(m => {
    const displayName = m.userName || m.userEmail || '?';
    const unit  = type === 'PERCENT' ? '%' : '₹';
    const row   = document.createElement('div');
    row.className = 'split-row';
    row.innerHTML = `
      <div class="split-row-name">
        <div class="avatar avatar-sm">${avatarInitials(displayName)}</div>
        ${esc(displayName)}
      </div>
      <input class="split-amount-input" type="number" min="0" step="0.01"
             id="split-${m.userId}" placeholder="0" oninput="updateSplitHint()">
      <span class="split-unit">${unit}</span>
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
  const v = document.getElementById('split-validation');
  if (state.splitType === 'EQUAL') { v.className = 'validation-msg'; return; }

  const total = parseFloat(document.getElementById('exp-amount').value) || 0;
  const sum   = getSplitValues().reduce((a, b) => a + b.value, 0);

  if (state.splitType === 'EXACT') {
    if (Math.abs(sum - total) < 0.01) {
      v.className = 'validation-msg ok';
      v.textContent = `Splits total ₹${sum.toFixed(2)} — matches amount.`;
    } else {
      v.className = 'validation-msg error';
      v.textContent = `Splits total ₹${sum.toFixed(2)}, amount is ₹${total.toFixed(2)}.`;
    }
  } else {
    if (Math.abs(sum - 100) < 0.01) {
      v.className = 'validation-msg ok';
      v.textContent = 'Percentages total 100%.';
    } else {
      v.className = 'validation-msg error';
      v.textContent = `Percentages total ${sum.toFixed(1)}% — must equal 100%.`;
    }
  }
}

async function handleAddExpense() {
  const desc   = document.getElementById('exp-desc').value.trim();
  const amount = parseFloat(document.getElementById('exp-amount').value);
  const paidBy = parseInt(document.getElementById('exp-paidby').value, 10);
  const errEl  = document.getElementById('expense-error');
  hideAlert(errEl);

  if (!amount || amount <= 0) { showAlert(errEl, 'Please enter a valid amount.'); return; }

  let splits = null;
  if (state.splitType !== 'EQUAL') {
    const vals = getSplitValues();
    const sum  = vals.reduce((a, b) => a + b.value, 0);
    if (state.splitType === 'EXACT'   && Math.abs(sum - amount) > 0.01) { showAlert(errEl, `Split amounts must total ₹${amount.toFixed(2)}.`); return; }
    if (state.splitType === 'PERCENT' && Math.abs(sum - 100)    > 0.01) { showAlert(errEl, `Percentages must total 100%.`); return; }
    splits = vals.filter(s => s.value > 0);
  }

  const btn = document.getElementById('btn-add-expense');
  setLoading(btn, true, 'Add expense');
  try {
    const body = {
      description:  desc || undefined,
      amount,
      paidByUserId: paidBy,
      groupId:      state.selectedGroup.id,
      splitType:    state.splitType,
    };
    if (splits) body.splits = splits.map(s => ({ userId: s.userId, value: s.value }));

    await api('POST', '/expenses', body);
    toast('Expense added.', 'success');
    closeModal('modal-expense');
    await loadBalances(state.selectedGroup.id);
  } catch (e) {
    showAlert(errEl, e.message);
  } finally {
    setLoading(btn, false, 'Add expense');
  }
}

// ─────────────────────────────────────────────────────────────────
// SETTLE UP
// ─────────────────────────────────────────────────────────────────
function openSettleModal() {
  document.getElementById('settle-amount').value = '';
  hideAlert(document.getElementById('settle-error'));
  const hintEl = document.getElementById('settle-hint');
  hintEl.classList.remove('visible');

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
  const match  = state.balances.find(b => b.debtorId === fromId && b.creditorId === toId);

  if (match) {
    hintEl.classList.add('visible');
    hintEl.textContent = `Suggested amount based on outstanding debt: ₹${Number(match.amount).toFixed(2)}`;
    document.getElementById('settle-amount').value = Number(match.amount).toFixed(2);
  } else {
    hintEl.classList.remove('visible');
  }
}

async function handleSettle() {
  const fromId = parseInt(document.getElementById('settle-from').value, 10);
  const toId   = parseInt(document.getElementById('settle-to').value,   10);
  const amount = parseFloat(document.getElementById('settle-amount').value);
  const errEl  = document.getElementById('settle-error');
  hideAlert(errEl);

  if (fromId === toId)       { showAlert(errEl, 'Payer and recipient must be different.'); return; }
  if (!amount || amount <= 0){ showAlert(errEl, 'Please enter a valid amount.'); return; }

  try {
    await api('POST', '/settlements', { groupId: state.selectedGroup.id, fromUserId: fromId, toUserId: toId, amount });
    toast('Settlement recorded.', 'success');
    closeModal('modal-settle');
    await loadBalances(state.selectedGroup.id);
  } catch (e) {
    showAlert(errEl, e.message);
  }
}

// ─────────────────────────────────────────────────────────────────
// MODAL UTILS
// ─────────────────────────────────────────────────────────────────
function openModal(id) {
  if (id === 'modal-expense') { openExpenseModal(); return; }
  if (id === 'modal-settle')  { openSettleModal();  return; }
  document.getElementById(id).classList.add('active');
}
function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

// ─────────────────────────────────────────────────────────────────
// TOAST
// ─────────────────────────────────────────────────────────────────
const TOAST_ICONS = {
  success: '#ic-check-circle',
  error:   '#ic-alert-circle',
  info:    '#ic-info',
};

function toast(message, type = 'info') {
  const el = document.createElement('div');
  el.className = 'toast';
  el.innerHTML = `<svg class="toast-${type}"><use href="${TOAST_ICONS[type] || TOAST_ICONS.info}"/></svg>${esc(message)}`;
  document.getElementById('toast-container').appendChild(el);
  setTimeout(() => {
    el.style.transition = 'opacity 0.3s, transform 0.3s';
    el.style.opacity    = '0';
    el.style.transform  = 'translateX(40px)';
    setTimeout(() => el.remove(), 320);
  }, 3200);
}

// ─────────────────────────────────────────────────────────────────
// FORM UTILS
// ─────────────────────────────────────────────────────────────────
function showAlert(el, msg) {
  el.querySelector('span').textContent = msg;
  el.classList.add('visible');
}
function hideAlert(el) {
  el.classList.remove('visible');
  const span = el.querySelector('span');
  if (span) span.textContent = '';
}
function setLoading(btn, loading, label) {
  if (loading) {
    btn.dataset.orig = btn.innerHTML;
    btn.innerHTML    = `<span class="spinner"></span>`;
    btn.disabled     = true;
  } else {
    btn.innerHTML = btn.dataset.orig || label || 'Submit';
    btn.disabled  = false;
  }
}

// ─────────────────────────────────────────────────────────────────
// STRING UTILS
// ─────────────────────────────────────────────────────────────────
function esc(s) {
  const d = document.createElement('div');
  d.appendChild(document.createTextNode(String(s || '')));
  return d.innerHTML;
}
function avatarInitials(name) {
  return (name || '?').split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
}
