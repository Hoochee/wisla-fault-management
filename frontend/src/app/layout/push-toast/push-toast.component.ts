import { Component, inject } from '@angular/core';

import { PushToastService, PushToastView } from '../../core/notifications/push-toast.service';

@Component({
  selector: 'app-push-toast',
  standalone: true,
  template: `
    <div class="toast-stack" aria-live="polite">
      <div class="toast-list">
        @for (toast of pushToast.toasts(); track toast.id) {
          <div class="toast" [class.clickable]="!!toast.eventId">
            <button type="button" class="toast-body" (click)="onOpen(toast)">
              <div class="toast-title">{{ toast.title }}</div>
              <div class="toast-message">{{ toast.message }}</div>
              @if (toast.eventId) {
                <div class="toast-action">Открыть событие →</div>
              }
            </button>
            <button
              type="button"
              class="toast-close"
              (click)="pushToast.dismiss(toast.id)"
              aria-label="Закрыть"
            >
              ×
            </button>
          </div>
        }
      </div>
      @if (pushToast.toasts().length >= 2) {
        <button
          type="button"
          class="dismiss-all"
          (click)="pushToast.dismissAll()"
        >
          Закрыть все
        </button>
      }
    </div>
  `,
  styles: [
    `
      .toast-stack {
        position: fixed;
        right: 1rem;
        bottom: 1rem;
        z-index: 1000;
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.5rem;
        max-width: min(22rem, calc(100vw - 2rem));
        max-height: calc(100vh - 2rem);
        pointer-events: none;
      }
      .toast-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        width: 100%;
        max-height: calc(100vh - 5rem);
        overflow-y: auto;
        overscroll-behavior: contain;
        pointer-events: auto;
      }
      .dismiss-all {
        flex-shrink: 0;
        pointer-events: auto;
        padding: 0.375rem 0.625rem;
        font-size: 0.6875rem;
        font-weight: 500;
        color: #9ca3af;
        background: rgba(37, 40, 48, 0.92);
        border: 1px solid rgba(99, 102, 241, 0.25);
        border-radius: 6px;
        cursor: pointer;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
      }
      .dismiss-all:hover {
        color: #d1d5db;
        border-color: rgba(99, 102, 241, 0.45);
        background: rgba(37, 40, 48, 1);
      }
      .toast {
        display: flex;
        align-items: stretch;
        width: 100%;
        background: #252830;
        border: 1px solid rgba(99, 102, 241, 0.45);
        border-radius: 8px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
        pointer-events: auto;
        overflow: hidden;
      }
      .toast.clickable .toast-body {
        cursor: pointer;
      }
      .toast-body {
        flex: 1;
        text-align: left;
        padding: 0.75rem 0.5rem 0.75rem 0.875rem;
        background: none;
        border: none;
        color: inherit;
      }
      .toast-body:hover {
        background: rgba(99, 102, 241, 0.08);
      }
      .toast-title {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #fff;
        margin-bottom: 0.25rem;
      }
      .toast-message {
        font-size: 0.75rem;
        color: #d1d5db;
        line-height: 1.35;
      }
      .toast-action {
        margin-top: 0.375rem;
        font-size: 0.6875rem;
        color: #a5b4fc;
      }
      .toast-close {
        flex-shrink: 0;
        width: 2rem;
        border: none;
        background: none;
        color: #6b7280;
        font-size: 1.125rem;
        line-height: 1;
        cursor: pointer;
      }
      .toast-close:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.05);
      }
    `,
  ],
})
export class PushToastComponent {
  readonly pushToast = inject(PushToastService);

  onOpen(toast: PushToastView): void {
    this.pushToast.open(toast);
  }
}
