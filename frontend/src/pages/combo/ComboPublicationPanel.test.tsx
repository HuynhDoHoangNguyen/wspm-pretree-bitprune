import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import ComboPublicationPanel from './ComboPublicationPanel';
import { ComboClient, ComboClientError } from './ComboClient';

test('publishes only on explicit click, prevents duplicate submission and refreshes committed data', async () => {
  let finish!: (v: unknown) => void;
  const publish = jest.fn().mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const refresh = jest.fn();
  render(<ComboPublicationPanel batchId="B1" client={{ publish } as unknown as ComboClient} onPublished={refresh}/>);
  expect(publish).not.toHaveBeenCalled();
  const button = screen.getByRole('button', { name: 'Công bố batch cho khách hàng' });
  fireEvent.click(button); fireEvent.click(button);
  expect(publish).toHaveBeenCalledTimes(1);
  expect(publish).toHaveBeenCalledWith('B1', { signal: expect.any(AbortSignal) });
  await act(async () => { finish({ batchId: 'B1', status: 'ACTIVE', publishedCount: 2 }); });
  expect(screen.getByRole('status')).toHaveTextContent('2 combo'); expect(refresh).toHaveBeenCalledTimes(1);
});
test('reports uncertain failure without replaying publication', async () => {
  const publish = jest.fn().mockRejectedValue(new Error('network'));
  const refresh = jest.fn();
  render(<ComboPublicationPanel batchId="B1" client={{ publish } as unknown as ComboClient} onPublished={refresh}/>);
  fireEvent.click(screen.getByRole('button', { name: 'Công bố batch cho khách hàng' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Chưa xác nhận');
  expect(publish).toHaveBeenCalledTimes(1); expect(refresh).not.toHaveBeenCalled();
});

test('draft explains why approved combos are not visible and backend rejection includes safe code/requestId', async () => {
  const publish = jest.fn().mockRejectedValue(new ComboClientError(409, 'INVALID_RESOURCE_STATE', 'request-test'));
  render(<ComboPublicationPanel batchId="B1" batchStatus="DRAFT" client={{ publish } as unknown as ComboClient} onPublished={jest.fn()}/>);
  expect(screen.getByText(/khởi động lại ứng dụng không thay đổi trạng thái/)).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Công bố batch cho khách hàng' }));
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Backend từ chối công bố'));
  expect(screen.getByRole('alert')).toHaveTextContent('request-test');
  expect(publish).toHaveBeenCalledTimes(1);
});
