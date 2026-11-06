const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function load(file, name) {
  const context = vm.createContext({window: {}, setTimeout, clearTimeout});
  vm.runInContext(fs.readFileSync(`src/main/resources/static/js/${file}`, 'utf8'), context);
  return context.window[name];
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}

function clock() {
  const timers = new Map(); let id = 0;
  return {
    setTimer(fn, delay) { assert.equal(delay, 3000); timers.set(++id, fn); return id; },
    clearTimer(id) { timers.delete(id); },
    async tick() { const entry = timers.entries().next().value; assert.ok(entry); timers.delete(entry[0]); await entry[1](); },
    size: () => timers.size
  };
}

test('poller has one timer, never overlaps slow reads and stops after terminal tasks', async () => {
  const polling = load('core/taskPolling.js', 'TaskPolling');
  const time = clock(), pending = deferred();
  let calls = 0, active = true;
  const poller = polling.create({...time, shouldPoll: () => active, poll: async () => { calls++; await pending.promise; }});
  poller.start(); poller.start();
  assert.equal(time.size(), 1);
  const running = time.tick();
  poller.start();
  assert.equal(time.size(), 0);
  assert.equal(calls, 1);
  active = false;
  pending.resolve(); await running;
  assert.equal(time.size(), 0);
});

test('stop cancels a timer and prevents an in-flight read from scheduling again', async () => {
  const polling = load('core/taskPolling.js', 'TaskPolling');
  const time = clock(), pending = deferred();
  const poller = polling.create({...time, shouldPoll: () => true, poll: () => pending.promise});
  poller.start(); poller.stop();
  assert.equal(time.size(), 0);
  poller.start();
  const running = time.tick();
  poller.stop(); pending.resolve(); await running;
  assert.equal(time.size(), 0);
  poller.start();
  assert.equal(time.size(), 1);
});

test('poll errors notify once and leave retry to an explicit start', async () => {
  const polling = load('core/taskPolling.js', 'TaskPolling');
  const time = clock(); let errors = 0;
  const poller = polling.create({...time, shouldPoll: () => true,
    poll: async () => { throw new Error('offline'); }, onError: error => { assert.equal(error.message, 'offline'); errors++; }});
  poller.start(); await time.tick();
  assert.equal(errors, 1); assert.equal(time.size(), 0);
  poller.start(); assert.equal(time.size(), 1);
});
