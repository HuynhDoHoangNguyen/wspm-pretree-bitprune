import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { authSession } from 'auth/session';
import { ComboApiError, personalCombo, readPublicCombo } from 'api/recommendation/combos';
import { PersonalCombos, PublicCombo } from 'models/recommendation/combos';
import ComboPopup from './ComboPopup';
import ComboPopupLink from './ComboPopupLink';

jest.mock('api/recommendation/combos', () => ({ ...jest.requireActual('api/recommendation/combos'), readPublicCombo: jest.fn(), personalCombo: jest.fn() }));
const read = readPublicCombo as jest.MockedFunction<typeof readPublicCombo>;
const personal = personalCombo as jest.MockedFunction<typeof personalCombo>;
const combo = { comboId: 'c', name: 'Compact combo', kind: 'MIXED_SEQUENCE', pricing: { currencyCode: 'VND' },
  items: ['Laptop', 'Mouse', 'Bag'].map((name, index) => ({ itemsetOrder: index ? 2 : 1, itemOrder: index || 1,
    quantity: 1, product: { productId: name, name, currentPrice: '0.10', availableQuantity: '10', imageUrl: null } })) } as PublicCombo;
const match: PersonalCombos = { policy: 'HISTORY_PREFIX_V1', evaluatedAt: '', items: [{ rank: 1, matchedItemsets: 1,
  nextItemsetOrder: 2, suggestedProductIds: ['Mouse', 'Bag'], combo }] };
beforeEach(() => {
  read.mockReset(); personal.mockReset();
  authSession.useStore.setState({ status: 'anonymous', accessToken: null, user: null });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: () => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() }) });
});
afterEach(() => { act(() => authSession.unauthorized()); });

it('does not request personal details for a guest', async () => {
  read.mockRejectedValue(new ComboApiError(404, 'NOT_FOUND'));
  render(<ComboPopup comboId="c" onClose={jest.fn()}/>);
  await screen.findByText(/Combo không khả dụng/);
  expect(personal).not.toHaveBeenCalled();
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
});

it('uses server-verified next-step products for mixed combos and clears them on logout', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'owner' });
  read.mockRejectedValue(new ComboApiError(404, 'NOT_FOUND')); personal.mockResolvedValue(match);
  const close = jest.fn(); render(<ComboPopup comboId="c" onClose={close}/>);
  expect(await screen.findByRole('checkbox', { name: 'Chọn Mouse' })).not.toBeChecked();
  expect(screen.getByRole('checkbox', { name: 'Chọn Bag' })).not.toBeChecked();
  expect(screen.queryByRole('checkbox', { name: 'Chọn Laptop' })).not.toBeInTheDocument();
  expect(personal).toHaveBeenCalledWith('owner', 'c', expect.any(AbortSignal));
  fireEvent.click(screen.getByRole('checkbox', { name: 'Chọn Bag' }));
  expect(screen.getByText(/: 0,1 VND$/)).toBeVisible();
  act(() => authSession.unauthorized());
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument(); expect(close).toHaveBeenCalled();
});

it('ignores a personal result held across identity changes and aborts its request', async () => {
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'owner' });
  read.mockRejectedValue(new ComboApiError(404, 'NOT_FOUND'));
  let finish!: (value: PersonalCombos) => void;
  personal.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  render(<ComboPopup comboId="c" onClose={jest.fn()}/>);
  await waitFor(() => expect(personal).toHaveBeenCalled());
  const signal = personal.mock.calls[0][2];
  act(() => authSession.unauthorized());
  await act(async () => finish(match));
  expect(signal.aborted).toBe(true); expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
});

it('opens over the current page and browser Back closes it without losing the page', async () => {
  read.mockResolvedValue({ ...combo, kind: 'SAME_ORDER_BUNDLE' });
  function Back() { const navigate = useNavigate(); return <button onClick={() => navigate(-1)}>Back</button>; }
  render(<MemoryRouter initialEntries={['/products?page=3']}><Back/><ComboPopupLink comboId="c"/></MemoryRouter>);
  fireEvent.click(screen.getByRole('link', { name: 'Xem chi tiết Combo' }));
  await screen.findByRole('checkbox', { name: 'Chọn Mouse' });
  fireEvent.click(screen.getByRole('button', { name: 'Back', hidden: true }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('carries current product context through the popup link and starts fresh on reopening',async()=>{
  read.mockResolvedValue({...combo,kind:'SAME_ORDER_BUNDLE'});
  render(<MemoryRouter><ComboPopupLink comboId="c" productId="Mouse"/></MemoryRouter>);
  fireEvent.click(screen.getByRole('link',{name:'Xem chi tiết Combo'}));
  const checkbox=await screen.findByRole('checkbox',{name:'Chọn Mouse'});
  expect(checkbox).not.toBeChecked();
  expect(checkbox.closest('[data-current-product]')).toHaveAttribute('data-current-product','true');
  fireEvent.click(checkbox);fireEvent.change(screen.getByLabelText('Số lượng Mouse'),{target:{value:'2'}});
  expect(screen.getByRole('button',{name:'Thêm vào giỏ hàng'})).toBeDisabled();
  fireEvent.click(screen.getByRole('button',{name:'Đóng',exact:true}));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('link',{name:'Xem chi tiết Combo'}));
  expect(await screen.findByRole('checkbox',{name:'Chọn Mouse'})).not.toBeChecked();
  expect(screen.getByLabelText('Số lượng Mouse')).toHaveValue(1);
});
