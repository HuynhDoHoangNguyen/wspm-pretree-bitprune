import React from 'react';
import { Navbar, ScrollArea } from '@mantine/core';
import {
  AddressBook,
  Award,
  Box,
  Building,
  BuildingWarehouse,
  Car,
  CurrencyDollar,
  FileBarcode,
  Fingerprint,
  Home,
  BuildingStore,
  List,
  Icon,
  Message,
  Users
} from 'tabler-icons-react';
import { useLocation } from 'react-router-dom';
import AdminNavigationGroup, { AdminNavigationLink } from './AdminNavigationGroup';
import useAppStore from 'stores/use-app-store';
import useDefaultNavbarStyles from 'components/DefaultNavbar/DefaultNavbar.styles';
import { useSharedAuth } from 'auth/session';
import DataMiningNavigation from 'pages/admin/data-mining/DataMiningNavigation';


interface NavbarLink {
  link: string;
  label: string;
  icon: Icon;
  childLinks?: NavbarChildLink[];
  disableForEmployee?: boolean;
}

interface NavbarChildLink {
  link: string;
  label: string;
}

const navbarLinks: NavbarLink[] = [
  {
    link: '/admin',
    label: 'Trang chủ Admin',
    icon: Home,
  },
  {
    link: '/admin/address',
    label: 'Địa chỉ',
    icon: AddressBook,
    childLinks: [
      {
        link: '/admin/address/province',
        label: 'Tỉnh thành',
      },
      {
        link: '/admin/address/district',
        label: 'Quận huyện',
      },
      {
        link: '/admin/address/ward',
        label: 'Phường xã',
      },
    ],
    disableForEmployee: true,
  },
  {
    link: '/admin/user',
    label: 'Người dùng',
    icon: Fingerprint,
    childLinks: [
      {
        link: '/admin/user/role',
        label: 'Quyền',
      },
    ],
    disableForEmployee: true,
  },
  {
    link: '/admin/employee',
    label: 'Nhân viên',
    icon: Building,
    childLinks: [
      {
        link: '/admin/employee/office',
        label: 'Văn phòng',
      },
      {
        link: '/admin/employee/department',
        label: 'Phòng ban',
      },
      {
        link: '/admin/employee/job-type',
        label: 'Loại hình công việc',
      },
      {
        link: '/admin/employee/job-level',
        label: 'Cấp bậc công việc',
      },
      {
        link: '/admin/employee/job-title',
        label: 'Chức danh công việc',
      },
    ],
    disableForEmployee: true,
  },
  {
    link: '/admin/customer',
    label: 'Khách hàng',
    icon: Users,
    childLinks: [
      {
        link: '/admin/customer/group',
        label: 'Nhóm khách hàng',
      },
      {
        link: '/admin/customer/status',
        label: 'Trạng thái khách hàng',
      },
      {
        link: '/admin/customer/resource',
        label: 'Nguồn khách hàng',
      },
    ],
    disableForEmployee: true,
  },
  {
    link: '/admin/product',
    label: 'Sản phẩm',
    icon: Box,
    childLinks: [
      {
        link: '/admin/category',
        label: 'Danh mục sản phẩm',
      },
      {
        link: '/admin/product/brand',
        label: 'Nhãn hiệu',
      },
      {
        link: '/admin/product/supplier',
        label: 'Nhà cung cấp',
      },
      {
        link: '/admin/product/unit',
        label: 'Đơn vị tính',
      },
      {
        link: '/admin/product/tag',
        label: 'Tag',
      },
      {
        link: '/admin/product/guarantee',
        label: 'Bảo hành',
      },
      {
        link: '/admin/product/property',
        label: 'Thuộc tính sản phẩm',
      },
      {
        link: '/admin/product/specification',
        label: 'Thông số sản phẩm',
      },
    ],
    disableForEmployee: true,
  },
  {
    link: '/admin/inventory',
    label: 'Tồn kho',
    icon: BuildingWarehouse,
    childLinks: [
      {
        link: '/admin/inventory/warehouse',
        label: 'Nhà kho',
      },
      {
        link: '/admin/inventory/purchase-order',
        label: 'Đơn mua hàng',
      },
      {
        link: '/admin/inventory/destination',
        label: 'Điểm nhập hàng',
      },
      {
        link: '/admin/inventory/docket',
        label: 'Phiếu nhập xuất kho',
      },
      {
        link: '/admin/inventory/docket-reason',
        label: 'Lý do phiếu NXK',
      },
      {
        link: '/admin/inventory/count',
        label: 'Phiếu kiểm kho',
      },
      {
        link: '/admin/inventory/transfer',
        label: 'Phiếu chuyển kho',
      },
    ],
  },
  {
    link: '/admin/order',
    label: 'Đơn hàng',
    icon: FileBarcode,
    childLinks: [
      {
        link: '/admin/order/resource',
        label: 'Nguồn đơn hàng',
      },
      {
        link: '/admin/order/cancellation-reason',
        label: 'Lý do hủy đơn hàng',
      },
    ],
  },
  {
    link: '/admin/waybill',
    label: 'Vận đơn',
    icon: Car,
    childLinks: [],
  },
  {
    link: '/admin/review',
    label: 'Đánh giá',
    icon: Message,
    childLinks: [],
  },
  {
    link: '/admin/reward-strategy',
    label: 'Điểm thưởng',
    icon: Award,
    childLinks: [],
    disableForEmployee: true,
  },
  {
    link: '/admin/voucher',
    label: 'Sổ quỹ',
    icon: CurrencyDollar,
    childLinks: [
      {
        link: '/admin/payment-method',
        label: 'Hình thức thanh toán',
      },
      {
        link: '/admin/promotion',
        label: 'Khuyến mãi',
      },
    ],
    disableForEmployee: true,
  },
];

export function DefaultNavbar() {
  const { opened } = useAppStore();
  const { classes } = useDefaultNavbarStyles();
  const location = useLocation();
  const pathname = location.pathname.replace(/\/+$/, '').toLowerCase();
  const { user } = useSharedAuth();
  const legacy = navbarLinks.filter(item => item.link !== '/admin');
  const legacyActive = legacy.some(item => pathname === item.link || pathname.startsWith(item.link + '/')
    || item.childLinks?.some(child => pathname === child.link || pathname.startsWith(child.link + '/')));
  return <Navbar p="sm" width={{ md: 250 }} hidden={!opened}>
    <Navbar.Section grow component={ScrollArea}>
      <AdminNavigationLink link="/admin" label="Trang chủ Admin" active={pathname.replace(/\/+$/, '') === '/admin'} icon={<Home className={classes.linkIcon}/>}/>
      <DataMiningNavigation/>
      <AdminNavigationGroup label="Quản lý cửa hàng" current={legacyActive} icon={<BuildingStore className={classes.linkIcon} size={22} aria-hidden/>}>
        {legacy.map(item => {
          const disabled = !!user && !user.roles.includes('ADMIN') && item.disableForEmployee;
          const current = pathname === item.link || pathname.startsWith(item.link + '/')
            || !!item.childLinks?.some(child => pathname === child.link || pathname.startsWith(child.link + '/'));
          if (!item.childLinks?.length) return <AdminNavigationLink key={item.link} link={item.link} label={item.label} active={current} disabled={disabled} icon={<item.icon className={classes.linkIcon}/>}/>;
          const childLinks = [{ link: item.link, label: 'Danh s\u00e1ch ' + item.label.toLowerCase() }, ...item.childLinks];
          const selected = childLinks.filter(child => pathname === child.link || pathname.startsWith(child.link + '/')).sort((a, b) => b.link.length - a.link.length)[0]?.link;
          return <AdminNavigationGroup key={item.link} label={item.label} current={current} disabled={disabled} icon={<item.icon className={classes.linkIcon}/>}>
            {childLinks.map(child =>
              <AdminNavigationLink key={child.link} {...child} active={selected === child.link} icon={<List className={classes.linkIcon} size={18} aria-hidden/>} disabled={disabled}/>)}
          </AdminNavigationGroup>;
        })}
      </AdminNavigationGroup>
    </Navbar.Section>
  </Navbar>;
}
