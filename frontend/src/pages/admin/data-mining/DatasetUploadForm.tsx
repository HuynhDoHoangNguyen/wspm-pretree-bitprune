import React, { FormEvent, useRef, useState } from 'react';
import { Alert, Anchor, Badge, Button, Group, Paper, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { FileSpreadsheet, Upload, X } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import { datasetApi, DatasetSnapshotResponse } from 'api/admin/data-mining';
import DatasetRequestError from './DatasetRequestError';
import { useDatasetRequest } from './useDatasetRequest';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

export default function DatasetUploadForm() {
  const [file, setFile] = useState<File | null>(null);
  const [validation, setValidation] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<DatasetSnapshotResponse | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const request = useDatasetRequest();
  const theme = useMantineTheme();

  function clearFile() {
    setFile(null);
    setValidation(null);
    request.clearError();
    if (input.current) input.current.value = '';
  }

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

  const isDark = theme.colorScheme === 'dark';

  return (
    <Paper withBorder radius="md" p="lg">
      <form aria-label="Upload CSV" onSubmit={submit} noValidate>
        <Stack>
          <Title order={3}>2. Upload CSV tạo snapshot</Title>
          <Text size="sm" color="dimmed">Chọn tệp vừa export hoặc CSV theo hợp đồng dữ liệu. Hệ thống sẽ lưu bản gốc; kiểm tra nội dung và chuyển đổi sequence được thực hiện ở bước sau.</Text>

          <div>
            <span style={{ display: 'block', fontSize: 14, fontWeight: 500, marginBottom: 6 }}>
              Tệp CSV
            </span>

            {/* Native input kept for standard file events and accessibility */}
            <input
              ref={input}
              id="dataset-csv-file"
              aria-label="Tệp CSV"
              type="file"
              accept=".csv"
              disabled={request.busy}
              aria-describedby="dataset-upload-help"
              style={{ display: 'none' }}
              onChange={event => {
                setFile(event.currentTarget.files?.[0] || null);
                setReceipt(null);
                setValidation(null);
                request.clearError();
              }}
            />

            {!file ? (
              <label
                htmlFor="dataset-csv-file"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 16px',
                  borderRadius: theme.radius.sm,
                  backgroundColor: isDark ? theme.colors.dark[5] : theme.colors.blue[0],
                  color: isDark ? theme.colors.blue[3] : theme.colors.blue[7],
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: request.busy ? 'not-allowed' : 'pointer',
                  border: `1px solid ${isDark ? theme.colors.dark[4] : theme.colors.blue[2]}`,
                  userSelect: 'none',
                  transition: 'background-color 150ms ease',
                }}
              >
                <Upload size={16} />
                <span>Chọn tệp CSV…</span>
              </label>
            ) : (
              <Paper
                withBorder
                p="xs"
                radius="sm"
                sx={{
                  backgroundColor: isDark ? theme.colors.dark[6] : theme.colors.gray[0],
                  borderColor: isDark ? theme.colors.dark[4] : theme.colors.gray[3],
                }}
              >
                <Group position="apart" noWrap>
                  <Group spacing="xs" noWrap style={{ minWidth: 0, overflow: 'hidden' }}>
                    <FileSpreadsheet size={22} color={theme.colors.teal[6]} style={{ flexShrink: 0 }} />
                    <Stack spacing={0} style={{ minWidth: 0, overflow: 'hidden' }}>
                      <Text size="sm" weight={500} lineClamp={1} title={file.name}>
                        {file.name}
                      </Text>
                      <Text size="xs" color="dimmed">
                        {formatBytes(file.size)}
                      </Text>
                    </Stack>
                  </Group>

                  <Group spacing="xs" noWrap style={{ flexShrink: 0 }}>
                    <label
                      htmlFor="dataset-csv-file"
                      style={{
                        fontSize: 12,
                        fontWeight: 500,
                        color: theme.colors.blue[6],
                        cursor: request.busy ? 'not-allowed' : 'pointer',
                        padding: '4px 8px',
                        borderRadius: theme.radius.xs,
                        userSelect: 'none',
                      }}
                    >
                      Đổi tệp
                    </label>

                    <span
                      tabIndex={0}
                      title="Bỏ chọn tệp"
                      aria-label="Bỏ chọn tệp"
                      onClick={request.busy ? undefined : clearFile}
                      onKeyDown={e => {
                        if (!request.busy && (e.key === 'Enter' || e.key === ' ')) clearFile();
                      }}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 24,
                        height: 24,
                        borderRadius: '50%',
                        cursor: request.busy ? 'not-allowed' : 'pointer',
                        color: theme.colors.red[6],
                        transition: 'background-color 150ms ease',
                      }}
                    >
                      <X size={16} />
                    </span>
                  </Group>
                </Group>
              </Paper>
            )}
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
