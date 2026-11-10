import React from 'react';
import { Alert, Button, Stack, Text } from '@mantine/core';
import { MiningRequest, WdmApiError } from 'api/admin/data-mining';
import { CreationPhase } from './useMiningRunCreation';
import { runErrorMessage } from './mining-run-lifecycle';

export default function MiningRunCreationFeedback({ phase, submitted, error, onReset }: {
  phase: CreationPhase; submitted: MiningRequest | null; error: WdmApiError | null; onReset: () => void;
}) {
  if (phase === 'ready') return null;
  return <Alert role={error ? 'alert' : 'status'} color={phase === 'uncertain' ? 'yellow' : error ? 'red' : 'blue'}
    title={phase === 'uncertain' ? 'Chưa xác định kết quả tạo run' : phase === 'submitting' ? 'Đang gửi yêu cầu tạo run' : phase === 'accepted' ? 'Đã tiếp nhận run' : 'Yêu cầu chưa được chấp nhận'}>
    <Stack spacing="xs">
      {submitted && <Text size="sm">Đã gửi: {submitted.snapshotId} · {submitted.algorithm} · {submitted.minsupMode}: {String(submitted.minsupValue)}</Text>}
      {error && <Text>{runErrorMessage(error)}</Text>}
      {phase === 'submitting' && <Text>Rời trang hoặc hủy chờ không hủy thực thi trên máy chủ.</Text>}
      {phase === 'uncertain' && <Text>Run có thể đã được tạo. Không tự gửi lại. Chưa có run ID đã xác nhận; hãy giữ mã yêu cầu để đối chiếu với quản trị viên.</Text>}
      {error?.requestId && <Text size="sm">Mã yêu cầu: {error.requestId}</Text>}
      {['uncertain', 'rejected'].includes(phase) && <><Text size="sm">Tạo yêu cầu mới có thể tạo thêm run. Bạn cần kiểm tra lại cấu hình trước khi gửi.</Text>
        <Button size="xs" variant="outline" onClick={onReset}>Tạo yêu cầu mới và kiểm tra lại</Button></>}
    </Stack>
  </Alert>;
}
