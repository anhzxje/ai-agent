/** Session-owned workspace refresh, task commits and polling. No DOM or global state. */
(function () {
  function create({api, state, isVisible = () => true, onStatus = () => {},
    onCommit = () => {}, onTasks = () => {}, onReset = () => {},
    onError = () => {}, afterLoad = async () => {}}) {
    let pending = null;
    let generation = 0;
    let sequence = 0;
    let committedTasks = 0;
    let controller = new AbortController();
    const poller = window.TaskPolling.create({
      shouldPoll: () => !!api.getCurrentUser() && isVisible() &&
        state.tasks.some(task => ['PENDING', 'PROCESSING'].includes(task.status)),
      poll: pollTasks,
      onError
    });
    function current(request) {
      if (request.generation !== generation) return false;
      if (!api.getCurrentUser()) { reset(); return false; }
      return request.token === api.getToken();
    }
    function requestContext() { return {token: api.getToken(), generation, sequence: ++sequence, signal: controller.signal}; }
    function commitTasks(tasks, request) {
      if (request.sequence < committedTasks) return;
      committedTasks = request.sequence;
      state.tasks = tasks;
      if (state.activeTask) state.activeTask = tasks.find(task => task.id === state.activeTask.id) || state.activeTask;
    }
    function refresh() {
      const request = requestContext();
      if (pending && pending.token === request.token && pending.generation === generation) return pending.promise;
      // Schedule work after pending is assigned, including for synchronously resolved adapters.
      request.promise = Promise.resolve().then(() => load(request)).finally(() => {
        if (pending === request) pending = null;
      });
      pending = request;
      return request.promise;
    }
    async function load(request) {
      if (!api.getCurrentUser()) {
        reset();
        return;
      }
      if (!current(request)) return;
      onStatus('loading');
      try {
        const [projects, tasks] = await Promise.all([api.getProjects({signal: request.signal}), api.getTasks({signal: request.signal})]);
        if (!current(request)) return;
        state.projects = projects || [];
        commitTasks(tasks || [], request);
        onCommit();
        await afterLoad(() => current(request));
        if (!current(request)) return;
        onStatus('ready');
        poller.start();
      } catch (error) {
        if (current(request)) onError(error);
      }
    }
    async function pollTasks() {
      const request = requestContext();
      // A refresh already reads tasks; await it instead of issuing a competing read.
      if (pending && pending.token === request.token && pending.generation === generation) {
        await pending.promise;
        return;
      }
      try {
        const tasks = await api.getTasks({signal: request.signal});
        if (!current(request) || !Array.isArray(tasks)) return;
        commitTasks(tasks, request);
        onTasks();
      } catch (error) {
        if (current(request)) throw error;
      }
    }
    function reset() {
      invalidate();
      state.currentUser = null;
      state.projects = [];
      state.tasks = [];
      onReset();
    }
    function invalidate() {
      generation++; pending = null; poller.stop();
      controller.abort(); controller = new AbortController();
    }
    return {refresh, start: poller.start, stop: poller.stop, invalidate};
  }
  window.WorkspaceSession = {create};
})();
