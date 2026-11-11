import React, { ChangeEvent, FormEvent, useState } from 'react';
import { Alert, Button, Input, InputWrapper, Paper, Stack, Text, Title } from '@mantine/core';
import { datasetApi } from 'api/admin/data-mining';
import { datasetPeriod } from './dataset-period';
import DatasetRequestError from './DatasetRequestError';
import { useDatasetRequest } from './useDatasetRequest';
import { downloadDatasetFile } from './downloadDatasetFile';

export default function DatasetExportForm() {
  const [first, setFirst] = useState(''), [last, setLast] = useState('');
  const [validation, setValidation] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const request = useDatasetRequest();

  function change(setter: (value: string) => void, value: string) {
    setter(value); setValidation(null); setSent(false); request.clearError();
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    if (request.busy) return;
    setSent(false); setValidation(null); request.clearError();
    let period: ReturnType<typeof datasetPeriod>;
    try { period = datasetPeriod(first, last); }
    catch (failure) { setValidation((failure as Error).message); return; }
    void request.run(signal => datasetApi.exportOrders(period, signal), result => {
      downloadDatasetFile(result);
      setSent(true);
    });
  }

  return (
    <Paper withBorder radius="md" p="lg">
      <form aria-label="Xuất dữ liệu đơn hàng" onSubmit={submit} noValidate>
        <Stack>
          <Title order={3}>1. Xuất CSV từ đơn hàng</Title>
          <Text size="sm" color="dimmed" data-secondary>Chọn kỳ theo giờ Việt Nam (UTC+07), tính cả ngày kết thúc. Chỉ xuất đơn đang ở trạng thái COMPLETED theo thời điểm đặt hàng.</Text>
          <InputWrapper id="dataset-first-day" label="Từ ngày" required>
            <Input id="dataset-first-day" type="date" required value={first} min="1000-01-02" max="9999-12-31" disabled={request.busy}
              onChange={(event: ChangeEvent<HTMLInputElement>) => change(setFirst, event.currentTarget.value)}/>
          </InputWrapper>
          <InputWrapper id="dataset-last-day" label="Đến hết ngày" required>
            <Input id="dataset-last-day" type="date" required value={last} min="1000-01-02" max="9999-12-31" disabled={request.busy}
              onChange={(event: ChangeEvent<HTMLInputElement>) => change(setLast, event.currentTarget.value)}/>
          </InputWrapper>
          {validation && <Alert role="alert" color="red">{validation}</Alert>}
          <DatasetRequestError error={request.error}/>
          {sent && <Text role="status" color="teal">Đã gửi tệp CSV để trình duyệt tải xuống. Bạn có thể chọn tệp đó ở phần upload.</Text>}
          <Button type="submit" loading={request.busy} disabled={request.busy}>{request.busy ? 'Đang xuất CSV…' : 'Export CSV'}</Button>
        </Stack>
      </form>
    </Paper>
  );
}
