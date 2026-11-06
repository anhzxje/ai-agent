/** One timer and one in-flight task read. Visibility/session decisions stay with the shell. */
(function () {
  function create({shouldPoll, poll, onError = () => {}, delay = 3000,
    setTimer = setTimeout, clearTimer = clearTimeout}) {
    let timer = null;
    let running = false;
    let enabled = false;

    function start() {
      enabled = true;
      if (running || timer !== null || !shouldPoll()) return;
      timer = setTimer(tick, delay);
    }

    function stop() {
      enabled = false;
      clearTimer(timer);
      timer = null;
    }

    async function tick() {
      timer = null;
      if (!enabled || !shouldPoll()) return;
      running = true;
      try {
        await poll();
      } catch (error) {
        enabled = false;
        onError(error);
      } finally {
        running = false;
        if (enabled) start();
      }
    }

    return {start, stop};
  }

  window.TaskPolling = {create};
})();
