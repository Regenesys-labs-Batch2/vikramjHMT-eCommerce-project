import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

function body(err: unknown): Partial<ApiError> | null {
  if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object') {
    return err.error as Partial<ApiError>;
  }
  return null;
}

export function errorCode(err: unknown): string | undefined {
  return body(err)?.code;
}

export function errorStatus(err: unknown): number | undefined {
  return err instanceof HttpErrorResponse ? err.status : undefined;
}

export function describeError(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) {
    return 'Unexpected error';
  }
  if (err.status === 0) {
    return 'Service unreachable. Please try again later.';
  }
  const b = body(err);
  if (b?.message) {
    const details = b.details?.map((d) => `${d.field}: ${d.issue}`).join('; ');
    return details ? `${b.message} (${details})` : b.message;
  }
  return `Request failed (${err.status})`;
}
