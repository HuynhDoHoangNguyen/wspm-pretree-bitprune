import BackButton from 'components/BackButton/BackButton';
import React from 'react';
import { Button, SimpleGrid, Stack } from '@mantine/core';
import { useSearchParams } from 'react-router-dom';
import AdminPageHeader from './AdminPageHeader';
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
  return (
    <div className={classes.root}><Stack spacing="xl">
      <BackButton to="/admin">Về trang chủ Admin</BackButton>
      <AdminPageHeader title="Dữ liệu khai phá" description="Quản lý snapshot và tiếp tục khai phá từ dữ liệu đã chuyển đổi."
        action={<Button onClick={() => select(creating ? 'list' : 'create')}>{creating ? 'Danh sách dữ liệu' : '+ Tạo dữ liệu mới'}</Button>}/>
      <section id="datasets-panel-list" aria-label="Danh sách dữ liệu" hidden={creating}>
        {!creating && <WorkspaceList/>}
      </section>
      <section id="datasets-panel-create" aria-label="Tạo dữ liệu mới" hidden={!creating}>
        <SimpleGrid mt="md" cols={2} spacing="lg" breakpoints={[{ maxWidth: 'md', cols: 1 }]} style={{ alignItems: 'start' }}>
          <DatasetExportForm/>
          <DatasetUploadForm/>
        </SimpleGrid>
      </section>
    </Stack></div>
  );
}
