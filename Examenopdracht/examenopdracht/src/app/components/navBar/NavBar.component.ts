import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'nav-bar-component',
  templateUrl: './NavBarComponent.html',
  standalone: true,
  imports: [RouterLink]
})
export class NavBarComponent {}
