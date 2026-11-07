import React from 'react';
import { Anchor, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { Link, useSearchParams } from 'react-router-dom';
import { Database, FileUpload } from 'tabler-icons-react';
import useTitle from 'hooks/use-title';
import DatasetExportForm from './DatasetExportForm';
import DatasetUploadForm from './DatasetUploadForm';
import useWorkspaceStyles from './workspace.styles';
import WorkspaceList from './WorkspaceList';

export default function DatasetsPage() {
  useTitle('Dữ liệu khai phá');
  const { classes } = useWorkspaceStyles();
  const [params, setParams] = useSearchParams();
  const creating = params.get('tab') === 'create';
  function select(tab: 'list' | 'create') {
    const next = new URLSearchParams(params); next.set('tab', tab); setParams(next);
  }
  function keyboard(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const tab = event.key === 'Home' ? 'list' : event.key === 'End' ? 'create' : creating ? 'list' : 'create';
    select(tab); document.getElementById('datasets-tab-' + tab)?.focus();
  }
  return (
    <div className={classes.root}><Stack spacing="xl">
      <Anchor component={Link} to="/admin">Trang chủ Admin / Hướng dẫn thực hiện</Anchor>
      <Stack spacing="sm" className={classes.hero}>
        <Text size="sm" color="blue" weight={700}>THƯ VIỆN DỮ LIỆU</Text>
        <Title order={2}>Dữ liệu khai phá</Title>
        <Text>Tìm lại snapshot, xem thông số và tiếp tục khai phá từ dữ liệu đã chuyển đổi. Danh sách được sắp xếp mới nhất trước.</Text>
      </Stack>
      <div role="tablist" aria-label="Dữ liệu khai phá" className={classes.tabs}>
        <button type="button" id="datasets-tab-list" role="tab" aria-selected={!creating} aria-controls="datasets-panel-list" tabIndex={creating ? -1 : 0}
          className={classes.tab} onClick={() => select('list')} onKeyDown={keyboard}><Database size={20}/>Danh sách dữ liệu</button>
        <button type="button" id="datasets-tab-create" role="tab" aria-label="Tạo dữ liệu mới · Xuất đơn hàng hoặc upload CSV" aria-selected={creating} aria-controls="datasets-panel-create" tabIndex={creating ? 0 : -1}
          className={classes.tab} onClick={() => select('create')} onKeyDown={keyboard}><FileUpload size={20}/><span>Tạo dữ liệu mới<span style={{ display: 'block', fontSize: 12, fontWeight: 400, marginTop: 3 }}>Xuất đơn hàng hoặc upload CSV</span></span></button>
      </div>
      <section id="datasets-panel-list" role="tabpanel" aria-labelledby="datasets-tab-list" hidden={creating}>
        {!creating && <WorkspaceList/>}
      </section>
      <section id="datasets-panel-create" role="tabpanel" aria-labelledby="datasets-tab-create" hidden={!creating}>
        <SimpleGrid mt="md" cols={2} spacing="lg" breakpoints={[{ maxWidth: 'md', cols: 1 }]} style={{ alignItems: 'start' }}>
          <DatasetExportForm/>
          <DatasetUploadForm/>
        </SimpleGrid>
      </section>
    </Stack></div>
  );
}
