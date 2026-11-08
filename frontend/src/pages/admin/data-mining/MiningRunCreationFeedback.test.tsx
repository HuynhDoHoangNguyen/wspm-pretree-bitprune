import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { WdmApiError } from 'api/admin/data-mining';
import { runInput } from 'api/admin/data-mining/mining-run-test-data';
import MiningRunCreationFeedback from './MiningRunCreationFeedback';
beforeEach(() => Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) }));
it('keeps submitted config and safe uncertainty without replaying on reset', () => {
  const reset = jest.fn(); render(<MiningRunCreationFeedback phase="uncertain" submitted={runInput}
    error={new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'SQL /private', 'req-123')} onReset={reset}/>);
  expect(screen.getByText('Chưa xác định kết quả tạo run')).toBeVisible(); expect(screen.getByText(/Run có thể đã được tạo/)).toBeVisible();
  expect(screen.getByText(/req-123/)).toBeVisible(); expect(screen.queryByText(/SQL/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /Tạo yêu cầu mới/ })); expect(reset).toHaveBeenCalledTimes(1);
});
