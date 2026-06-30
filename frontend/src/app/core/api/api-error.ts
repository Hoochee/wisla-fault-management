import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from './api.models';

export function formatApiError(err: HttpErrorResponse, fallback: string): string {
  const body = err.error as ErrorResponse | null | undefined;
  if (!body?.message) {
    return fallback;
  }
  if (body.error === 'canvas_validation' && body.details?.length) {
    const detailText = body.details
      .map((d) => d.message)
      .filter((m): m is string => Boolean(m))
      .join(', ');
    return detailText ? `${body.message} (${detailText})` : body.message;
  }
  return body.message;
}
