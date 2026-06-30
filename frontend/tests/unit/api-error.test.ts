import { describe, expect, it } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { formatApiError } from '../../src/app/core/api/api-error';

describe('formatApiError', () => {
  it('returns canvas_validation message with detail codes', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: {
        error: 'canvas_validation',
        message: 'Rule canvas must include at least one trigger with triggerType=stream',
        details: [{ field: 'canvas.nodes', message: 'missing_stream_trigger' }],
      },
    });
    expect(formatApiError(err, 'fallback')).toBe(
      'Rule canvas must include at least one trigger with triggerType=stream (missing_stream_trigger)',
    );
  });

  it('returns fallback when body has no message', () => {
    const err = new HttpErrorResponse({ status: 500, error: null });
    expect(formatApiError(err, 'Не удалось сохранить правило')).toBe('Не удалось сохранить правило');
  });
});
