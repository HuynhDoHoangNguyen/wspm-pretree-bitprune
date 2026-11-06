import React, { FormEvent, useRef, useState } from 'react';
import { Alert, Anchor, Badge, Button, Group, Paper, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { ArrowRight, Check, FileSpreadsheet, Upload, X } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import { datasetApi, DatasetSnapshotResponse } from 'api/admin/data-mining';
import DatasetRequestError from './DatasetRequestError';
import { useDatasetRequest } from './useDatasetRequest';
import CopyValue from './CopyValue';

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
          <Text size="sm" color="dimmed" data-secondary>Chọn tệp vừa export hoặc CSV theo hợp đồng dữ liệu. Hệ thống sẽ lưu bản gốc; kiểm tra nội dung và chuyển đổi sequence được thực hiện ở bước sau.</Text>

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
                role="button" tabIndex={request.busy ? -1 : 0} aria-disabled={request.busy}
                onKeyDown={event => { if (!request.busy && ['Enter', ' '].includes(event.key)) { event.preventDefault(); input.current?.click(); } }}
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
                      <Text size="xs" color="dimmed" data-secondary>
                        {formatBytes(file.size)}
                      </Text>
                    </Stack>
                  </Group>

                  <Group spacing="xs" noWrap style={{ flexShrink: 0 }}>
                    <label
                      htmlFor="dataset-csv-file"
                      role="button" tabIndex={request.busy ? -1 : 0} aria-disabled={request.busy}
                      onKeyDown={event => { if (!request.busy && ['Enter', ' '].includes(event.key)) { event.preventDefault(); input.current?.click(); } }}
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
                      role="button" aria-disabled={request.busy}
                      tabIndex={0}
                      title="Bỏ chọn tệp"
                      aria-label="Bỏ chọn tệp"
                      onClick={request.busy ? undefined : clearFile}
                      onKeyDown={e => {
                        if (!request.busy && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); clearFile(); }
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

          <Text id="dataset-upload-help" size="sm" color="dimmed" data-secondary>Giới hạn dung lượng do server quyết định. Chưa có tệp được chọn thì chưa có yêu cầu upload.</Text>
          {validation && <Alert role="alert" color="red">{validation}</Alert>}
          <DatasetRequestError error={request.error} upload/>
          <Button type="submit" loading={request.busy} disabled={request.busy || !file}>{request.busy ? 'Đang upload…' : 'Upload CSV'}</Button>
        </Stack>
      </form>
      {receipt && <Paper component="section" aria-label="Snapshot đã tạo" mt="xl" p="lg" radius="md" withBorder
        sx={{ borderColor: isDark ? theme.colors.teal[8] : theme.colors.teal[3], background: isDark ? theme.colors.dark[6] : theme.colors.teal[0] }}>
        <Stack spacing="md">
          <Group spacing="xs"><Check size={22} color={theme.colors.teal[isDark ? 3 : 8]}/><Text role="status" weight={700}>Đã lưu dữ liệu thành công</Text></Group>
          <Text weight={600} style={{ overflowWrap: 'anywhere' }}>{receipt.sourceName}</Text>
          <div><Badge color="yellow" variant="light" sx={{ color: isDark ? '#fde68a' : '#713f12', background: isDark ? '#422006' : '#fef3c7' }}>Chờ kiểm tra dữ liệu</Badge></div>
          <Text size="sm">Bước tiếp theo: kiểm tra nội dung CSV. Bạn sẽ xem báo cáo và chuyển đổi dữ liệu tại trang chi tiết snapshot.</Text>
          <Button component={Link} to={'/admin/datasets/' + receipt.snapshotId + '?tab=validation'} rightIcon={<ArrowRight size={17}/>}>Tiếp tục kiểm tra dữ liệu</Button>
          <Anchor component={Link} to={'/admin/datasets/' + receipt.snapshotId}>Xem chi tiết snapshot</Anchor>
          <div><Text size="sm" color="dimmed" data-secondary>Snapshot ID</Text><CopyValue label="Snapshot ID" value={receipt.snapshotId}/></div>
        </Stack>
      </Paper>}
    </Paper>
  );
}
