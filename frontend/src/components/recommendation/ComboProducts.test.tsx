import React from 'react';
import {fireEvent,render,screen,within} from '@testing-library/react';
import {PublicCombo} from 'models/recommendation/combos';
import ComboProducts from './ComboProducts';

const combo={comboId:'c',kind:'SAME_ORDER_BUNDLE',pricing:{currencyCode:'VND'},items:[
  {itemsetOrder:1,itemOrder:1,quantity:9,product:{productId:'current',name:'Mouse',currentPrice:'0.10',imageUrl:null,availableQuantity:'3'}},
  {itemsetOrder:1,itemOrder:2,quantity:7,product:{productId:'other',name:'Keyboard',currentPrice:'0.20',imageUrl:null,availableQuantity:'2'}},
]} as PublicCombo;

test('starts unchecked at quantity one, highlights context only, and brightens the other product after selecting',()=>{
  const {container}=render(<ComboProducts combo={combo} contextProductId="current"/>);
  for(const input of screen.getAllByRole('checkbox'))expect(input).not.toBeChecked();
  expect(screen.getByLabelText('Số lượng Mouse')).toHaveValue(1);
  expect(screen.getByLabelText('Số lượng Keyboard')).toBeDisabled();
  expect(screen.getByText(/Tổng giá đang chọn: 0 VND/)).toBeVisible();
  const current=container.querySelector('[data-current-product="true"]');
  expect(current).toHaveAttribute('aria-current','true');
  const other=screen.getByRole('checkbox',{name:'Chọn Keyboard'}).closest('[data-selected]');
  if(!(other instanceof HTMLElement))throw new Error('Missing product row');
  expect(other).toHaveAttribute('data-selected','false');
  fireEvent.click(within(other).getByRole('checkbox'));
  expect(other).toHaveAttribute('data-selected','true');
  expect(screen.getByLabelText('Số lượng Keyboard')).toBeEnabled();
  expect(screen.getByText(/Tổng giá đang chọn: 0,2 VND/)).toBeVisible();
  fireEvent.click(within(other).getByRole('checkbox'));
  expect(other).toHaveAttribute('data-selected','false');
  expect(screen.getByText(/Tổng giá đang chọn: 0 VND/)).toBeVisible();
  expect(screen.getByRole('button',{name:'Thêm vào giỏ hàng'})).toBeDisabled();
  expect(screen.queryByText('Sắp hỗ trợ')).not.toBeInTheDocument();
});

test('enforces exact integer min/max, updates decimal totals and recovers an empty input on blur',()=>{
  render(<ComboProducts combo={combo}/>);
  fireEvent.click(screen.getByRole('checkbox',{name:'Chọn Keyboard'}));
  const quantity=screen.getByLabelText('Số lượng Keyboard');
  expect(quantity).toHaveAttribute('min','1');expect(quantity).toHaveAttribute('max','2');
  fireEvent.change(quantity,{target:{value:'2'}});
  expect(screen.getByText(/Tổng giá đang chọn: 0,4 VND/)).toBeVisible();
  for(const value of ['0','-1','1.5','3','1e2']){fireEvent.change(quantity,{target:{value}});expect(quantity).toHaveValue(2);}
  fireEvent.change(quantity,{target:{value:''}});
  expect(screen.getByText(/Tổng giá đang chọn: —/)).toBeVisible();
  fireEvent.blur(quantity);expect(quantity).toHaveValue(1);
});

test('zero and unknown stock cannot be selected and stock reductions invalidate quantities',()=>{
  const view=render(<ComboProducts combo={{...combo,items:combo.items.map((i,index)=>({...i,product:{...i.product,availableQuantity:index?'0':null}}))}}/>);
  for(const input of screen.getAllByRole('checkbox'))expect(input).toBeDisabled();
  expect(screen.getByText('Hết hàng')).toBeVisible();expect(screen.getByText('Chưa có thông tin tồn kho')).toBeVisible();
  view.rerender(<ComboProducts combo={combo}/>);
  fireEvent.click(screen.getByRole('checkbox',{name:'Chọn Keyboard'}));
  fireEvent.change(screen.getByLabelText('Số lượng Keyboard'),{target:{value:'2'}});
  view.rerender(<ComboProducts combo={{...combo,items:combo.items.map(i=>({...i,product:{...i.product,availableQuantity:'1'}}))}}/>);
  expect(screen.getByText('Nhập số lượng hợp lệ.')).toBeVisible();
  expect(screen.getByText(/Tổng giá đang chọn: —/)).toBeVisible();
});
