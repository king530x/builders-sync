// Extraction des séances : semaine courante + WEEKS semaines suivantes.
(function (WEEKS) {
  window.__bsResult = '';
  (async function () {
    try {
      const fc = PrimeFaces.widgets.schedule.jqc;
      const all = {};
      const w = ms => new Promise(r => setTimeout(r, ms));
      const idle = async () => {
        await w(1500);
        for (let n = 0; n < 30; n++) {
          const q = PrimeFaces.ajax && PrimeFaces.ajax.Queue;
          if (!q || q.isEmpty()) break;
          await w(500);
        }
        await w(1000);
      };
      const grab = () => fc.fullCalendar('clientEvents').forEach(e => {
        const t = (e.title || '').split('\n');
        if (t.length > 5 && e.start && e.end && t[5].trim())
          all[t[5].trim()] = { id: t[5].trim(), start: e.start.format('YYYY-MM-DD[T]HH:mm:ss'),
                               end: e.end.format('YYYY-MM-DD[T]HH:mm:ss'), lines: t };
      });
      fc.fullCalendar('today'); await idle(); grab();
      for (let i = 0; i < WEEKS; i++) { fc.fullCalendar('next'); await idle(); grab(); }
      fc.fullCalendar('today');
      window.__bsResult = JSON.stringify(Object.values(all));
    } catch (e) {
      window.__bsResult = 'ERR:' + e;
    }
  })();
  return 'started';
})
