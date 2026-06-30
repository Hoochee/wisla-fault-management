import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { PushNotification } from '../api/api.models';
import { FmApiService } from '../api/fm-api.service';

export interface PushToastView extends PushNotification {
  dismissible: true;
}

@Injectable({ providedIn: 'root' })
export class PushToastService {
  private readonly api = inject(FmApiService);
  private readonly router = inject(Router);

  readonly toasts = signal<PushToastView[]>([]);

  private pollSub?: Subscription;
  private lastSinceMs = 0;
  private lastSince = new Date(0).toISOString();
  private readonly seenIds = new Set<string>();

  start(): void {
    if (this.pollSub) return;
    this.pollOnce();
    this.pollSub = interval(5_000)
      .pipe(switchMap(() => this.api.getPushNotifications(this.lastSince)))
      .subscribe({
        next: (res) => this.ingest(res.items),
        error: () => {
          /* ignore transient API errors during polling */
        },
      });
  }

  stop(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
  }

  dismiss(id: string): void {
    this.toasts.update((items) => items.filter((t) => t.id !== id));
  }

  dismissAll(): void {
    this.toasts.set([]);
  }

  open(toast: PushToastView): void {
    if (toast.eventId) {
      void this.router.navigate(['/console', toast.eventId]);
    }
    this.dismiss(toast.id);
  }

  private pollOnce(): void {
    this.api.getPushNotifications(this.lastSince).subscribe({
      next: (res) => this.ingest(res.items),
      error: () => {
        /* ignore */
      },
    });
  }

  private ingest(items: PushNotification[]): void {
    if (items.length === 0) return;

    let cursorMs = this.lastSinceMs;
    const fresh: PushToastView[] = [];
    for (const item of items) {
      const ts = Date.parse(item.createdAt);
      if (!Number.isNaN(ts) && ts > cursorMs) {
        cursorMs = ts;
      }
      if (this.seenIds.has(item.id)) continue;
      this.seenIds.add(item.id);
      fresh.push({ ...item, dismissible: true });
    }

    if (cursorMs > this.lastSinceMs) {
      this.lastSinceMs = cursorMs;
      this.lastSince = new Date(cursorMs).toISOString();
    }

    if (fresh.length > 0) {
      this.toasts.update((current) => [...current, ...fresh]);
    }
  }
}
