import React from 'react';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, Group, Stack, Text, Title } from '@mantine/core';
import { ArrowRight } from 'tabler-icons-react';
import useTitle from 'hooks/use-title';
import useWorkspaceStyles from './workspace.styles';

const steps = [
  { title: 'Chuẩn bị CSV', description: 'Xuất lịch sử đơn hàng theo khoảng thời gian hoặc chuẩn bị CSV đúng định dạng.', result: 'Tệp CSV trên máy của bạn.' },
  { title: 'Tạo snapshot', description: 'Upload CSV để lưu một bản dữ liệu gốc phục vụ các bước tiếp theo.', result: 'Snapshot ID và liên kết đến trang chi tiết.' },
  { title: 'Kiểm tra dữ liệu', description: 'Mở snapshot, kiểm tra nội dung và xem những dòng hoặc đơn bị loại sau làm sạch.', result: 'Báo cáo xác nhận dữ liệu đủ điều kiện chuyển đổi.' },
  { title: 'Chuyển đổi sequence', description: 'Chuyển dữ liệu đã kiểm tra sang chuỗi sản phẩm, xem thống kê và tải kết quả.', result: 'Tệp SPMF và mapping sản phẩm.' },
  { title: 'Xem trước & kiểm tra cấu hình', description: 'Dùng snapshot đã chuyển đổi thành công, có thống kê và tệp kết quả được máy chủ xác minh. Xem trước cho biết dataset và ngưỡng; kiểm tra cấu hình xác nhận phạm vi được hỗ trợ.', result: 'Cấu hình được kiểm tra; cả hai thao tác chưa chạy khai phá hoặc tạo lần chạy.', path: '/admin/mining-runs', action: 'Mở cấu hình khai phá' },
  { title: 'Xem Pattern của run đã lưu', description: 'Tra cứu mẫu, cấu trúc itemset và sản phẩm của các lần chạy đã có kết quả lưu trên máy chủ.', result: 'Danh sách và chi tiết Pattern; cần run có dữ liệu đã lưu.', path: '/admin/patterns', action: 'Mở Pattern Explorer' },
  { title: 'Xem & review Combo đã lưu', description: 'Lọc Combo theo batch, trạng thái và loại. Quyền duyệt hoặc từ chối phụ thuộc điều kiện máy chủ xác nhận.', result: 'Danh sách và chi tiết Combo; cần batch đã lưu. Chưa tự sinh Combo từ snapshot.', path: '/admin/combos', action: 'Mở quản lý Combo' },
];
export default function AdminHomePage() {
  useTitle('Trang chủ Admin');
  const { classes } = useWorkspaceStyles();
  return <div className={classes.root}><Stack spacing="xl">
    <div className={classes.hero}><Stack spacing="sm">
      <Text color="blue" weight={700} size="sm">KHÔNG GIAN QUẢN TRỊ</Text>
      <Title order={1}>Từ dữ liệu đơn hàng đến combo sản phẩm</Title>
      <Text size="md">Bắt đầu từ dữ liệu gốc. Thực hiện từng bước, kiểm tra kết quả trước khi chuyển sang bước tiếp theo.</Text>
      <Group mt="sm"><Button component={Link} to="/admin/datasets" rightIcon={<ArrowRight size={18}/>}>Bắt đầu với dữ liệu</Button>
        <Button component={Link} to="/admin/account" variant="white">Thông tin tài khoản</Button></Group>
    </Stack></div>
    <Alert color="blue" title="Chuẩn bị dữ liệu và tra cứu kết quả đã lưu">
      Đã có chuyển đổi sequence, thống kê, tải SPMF/mapping, xem trước dataset/ngưỡng và kiểm tra cấu hình.
      Chạy khai phá, công bố kết quả run và tự sinh Combo đang chờ tích hợp. Pattern và Combo hiện đọc dữ liệu đã lưu.
      Preview/Validate không tự chuyển đổi snapshot chưa đủ điều kiện.
    </Alert>
    <div><Title order={2}>Lộ trình thực hiện</Title><Text color="dimmed" data-secondary mt={4}>Mỗi snapshot có trang chi tiết riêng để tiếp tục từ trạng thái đã lưu.</Text></div>
    <div className={classes.grid}>{steps.map((step, index) => <section className={classes.step} key={step.title} aria-label={'Bước ' + (index + 1) + ': ' + step.title}>
      <span className={classes.number}>{index + 1}</span><Stack spacing="sm" style={{ minWidth: 0 }}>
        <Group spacing="xs"><Title order={3} style={{ fontSize: 18 }}>{step.title}</Title>{index >= 5 && <Badge color="blue">Cần kết quả đã lưu</Badge>}</Group>
        <Text size="sm">{step.description}</Text><Text size="sm" color="dimmed" data-secondary><strong>Kết quả:</strong> {step.result}</Text>
        <Button component={Link} to={step.path || '/admin/datasets'} variant="light" compact rightIcon={<ArrowRight size={15}/>}>{step.action || (index < 2 ? 'Mở trang dữ liệu' : 'Đến trang dữ liệu')}</Button>
      </Stack>
    </section>)}</div>
  </Stack></div>;
}
