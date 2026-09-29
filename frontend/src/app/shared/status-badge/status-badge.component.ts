import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span class="badge" [class.connected]="connected()" [class.disconnected]="!connected()">
      <span class="dot"></span>
      <span class="label">{{ label() }}</span>
      <span class="value">{{ connected() ? connectedText() : disconnectedText() }}</span>
    </span>
  `,
  styleUrl: './status-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly connected = input<boolean>(false);
  readonly connectedText = input<string>('CONNECTED');
  readonly disconnectedText = input<string>('DISCONNECTED');
}
