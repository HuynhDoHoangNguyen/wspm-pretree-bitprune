import React, { useState } from 'react';
import { Box, createStyles, Grid, Group, Stack, Text, useMantineTheme } from '@mantine/core';
import { Car, HeartHandshake, Stars } from 'tabler-icons-react';
import { ClientCarousel } from 'components';

const useStyles = createStyles((theme) => ({
  banner: {
    aspectRatio: '1903 / 580',
    backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.gray[1],
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    '& img': { display: 'block', width: '100%', height: '100%', objectFit: 'contain' },
  },
  rightBanner: {
    flexWrap: 'unset',
    backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[4] : theme.colors.gray[1],
    borderRadius: theme.radius.md,
  },
}));

const banners = Array.from({ length: 6 }, (_, index) => ({
  src: `/images/banners/banner${String(index + 1).padStart(2, '0')}.webp`,
  alt: `Giới thiệu sản phẩm ${index + 1}`,
}));

function BannerSlide({ src, alt }: { src: string; alt: string }) {
  const { classes } = useStyles();
  const [failed, setFailed] = useState(false);
  return <Box className={classes.banner}>
    {failed ? <Text color="dimmed" role="status">Banner hiện chưa khả dụng.</Text>
      : <img src={src} alt={alt} onError={() => setFailed(true)}/>}
  </Box>;
}

function ClientHomeBanner() {
  const theme = useMantineTheme();
  const { classes } = useStyles();

  return (
    <Grid>
      <Grid.Col md={7} lg={8}>
        <ClientCarousel>
          {banners.map(banner => <BannerSlide key={banner.src} {...banner}/>)}
        </ClientCarousel>
      </Grid.Col>
      <Grid.Col md={5} lg={4}>
        <Stack>
          <Group py="sm" px="md" className={classes.rightBanner}>
            <Car size={65} strokeWidth={1}/>
            <Stack spacing={theme.spacing.xs / 4}>
              <Text size="md" weight={500}>Miễn phí vận chuyển</Text>
              <Text size="sm">100% đơn hàng đều được miễn phí vận chuyển khi thanh toán trước.</Text>
            </Stack>
          </Group>
          <Group py="sm" px="md" className={classes.rightBanner}>
            <Stars size={65} strokeWidth={1}/>
            <Stack spacing={theme.spacing.xs / 4}>
              <Text size="md" weight={500}>Bảo hành tận tâm</Text>
              <Text size="sm">Bất kể giấy tờ thế nào, công ty luôn cam kết sẽ hỗ trợ khách hàng tới cùng.</Text>
            </Stack>
          </Group>
          <Group py="sm" px="md" className={classes.rightBanner}>
            <HeartHandshake size={65} strokeWidth={1}/>
            <Stack spacing={theme.spacing.xs / 4}>
              <Text size="md" weight={500}>Đổi trả 1-1 hoặc hoàn tiền</Text>
              <Text size="sm">Nếu phát sinh lỗi hoặc bạn cảm thấy sản phẩm chưa đáp ứng được nhu cầu.</Text>
            </Stack>
          </Group>
        </Stack>
      </Grid.Col>
    </Grid>
  );
}

export default ClientHomeBanner;
