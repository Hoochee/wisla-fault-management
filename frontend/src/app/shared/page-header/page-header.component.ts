import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-header">
      <div>
        <h1>{{ title }}</h1>
        @if (subtitle) {
          <p class="subtitle">{{ subtitle }}</p>
        }
      </div>
      @if (actionLabel && actionLink) {
        <a class="btn-primary" [routerLink]="actionLink">{{ actionLabel }}</a>
      }
    </div>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 1.5rem;
        gap: 1rem;
      }
      h1 {
        margin: 0;
        font-size: 1.5rem;
        font-weight: 600;
        color: #fff;
      }
      .subtitle {
        margin: 0.25rem 0 0;
        color: #9ca3af;
        font-size: 0.875rem;
      }
      .btn-primary {
        padding: 0.5rem 1rem;
        background: #4a9eff;
        color: #fff;
        border-radius: 6px;
        text-decoration: none;
        font-size: 0.875rem;
        white-space: nowrap;
      }
      .btn-primary:hover {
        background: #3a8eef;
      }
    `,
  ],
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle?: string;
  @Input() actionLabel?: string;
  @Input() actionLink?: string;
}
