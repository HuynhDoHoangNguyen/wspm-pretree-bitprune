import React, { FormEvent, useRef, useState } from 'react';
import { Alert, Anchor, Badge, Button, Paper, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { datasetApi, DatasetSnapshotResponse } from 'api/admin/data-mining';
import DatasetRequestError from './DatasetRequestError';
import { useDatasetRequest } from './useDatasetRequest';

export default function DatasetUploadForm() {
  const [file, setFile] = useState<File | null>(null);
  const [validation, setValidation] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<DatasetSnapshotResponse | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const request = useDatasetRequest();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (request.busy) return;
    setReceipt(null); setValidation(null); request.clearError();
    if (!file) { setValidation('Vui lòng chọn một tệp CSV.'); return; }
    if (file.size === 0) { setValidation('Tệp đang rỗng. Vui lòng chọn tệp CSV có nội dung.'); return; }
    if (!/\.csv$/i.test(file.name)) { setValidation('Vui lòng chọn tệp có phần mở rộng .csv.'); return; }
    void request.run(signal => datasetApi.upload(file, signal), result => {
      setReceipt(result); setFile(null);
      if (input.current) input.current.value = '';
    });
  }

  return (
    <Paper withBorder radius="md" p="lg">
      <form aria-label="Upload CSV" onSubmit={submit} noValidate>
        <Stack>
          <Title order={3}>2. Upload CSV tạo snapshot</Title>
          <Text size="sm" color="dimmed">Chọn tệp vừa export hoặc CSV theo hợp đồng dữ liệu. Hệ thống sẽ lưu bản gốc; kiểm tra nội dung và chuyển đổi sequence được thực hiện ở bước sau.</Text>
          <div>
            <label htmlFor="dataset-csv-file">Tệp CSV</label>
            <input ref={input} id="dataset-csv-file" type="file" accept=".csv" disabled={request.busy}
              aria-describedby="dataset-upload-help" style={{ display: 'block', marginTop: 8, maxWidth: '100%' }}
              onChange={event => {
                setFile(event.currentTarget.files?.[0] || null); setReceipt(null); setValidation(null); request.clearError();
              }}/>
          </div>
          <Text id="dataset-upload-help" size="sm" color="dimmed">Giới hạn dung lượng do server quyết định. Chưa có tệp được chọn thì chưa có yêu cầu upload.</Text>
          {validation && <Alert role="alert" color="red">{validation}</Alert>}
          <DatasetRequestError error={request.error} upload/>
          {receipt && (
            <Alert role="status" title="Đã tạo snapshot" color="teal">
              <Stack spacing={4}>
                <Text size="sm">Snapshot ID: {receipt.snapshotId}</Text>
                <Anchor component={Link} to={'/admin/datasets/' + receipt.snapshotId}>Xem chi tiết snapshot</Anchor>
                <Text size="sm" style={{ overflowWrap: 'anywhere' }}>Nguồn CSV: {receipt.sourceName}</Text>
                <Badge color="yellow" variant="light">PENDING — Chưa kiểm tra dữ liệu</Badge>
                <Text size="sm">Chưa chuyển đổi sequence, chưa tính thống kê và chưa thể chạy khai phá.</Text>
              </Stack>
            </Alert>
          )}
          <Button type="submit" loading={request.busy} disabled={request.busy || !file}>{request.busy ? 'Đang upload…' : 'Upload CSV'}</Button>
        </Stack>
      </form>
    </Paper>
  );
}
