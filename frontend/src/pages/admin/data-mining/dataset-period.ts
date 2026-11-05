import { ExportPeriod } from 'api/admin/data-mining/datasets';

function calendarDay(value: string): number {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('Chọn đủ ngày bắt đầu và ngày kết thúc.');
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (year < 1000 || year > 9999 || date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    throw new Error('Ngày không hợp lệ hoặc ngoài phạm vi hỗ trợ.');
  }
  return date.getTime();
}

/** Business display uses fixed UTC+07; date-only inputs never pass through the host timezone. */
export function datasetPeriod(firstDay: string, lastDay: string): ExportPeriod {
  const first = calendarDay(firstDay), last = calendarDay(lastDay);
  if (first > last) throw new Error('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
  const offset = 7 * 60 * 60 * 1000;
  const from = new Date(first - offset), to = new Date(last + 24 * 60 * 60 * 1000 - offset);
  if (from.getUTCFullYear() < 1000 || to.getUTCFullYear() > 9999) throw new Error('Khoảng ngày nằm ngoài phạm vi hỗ trợ.');
  return { fromTime: from.toISOString(), toTime: to.toISOString() };
}
