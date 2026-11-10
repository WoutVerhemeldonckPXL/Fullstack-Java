import { Component } from '@angular/core';
import { NavBarComponent } from '../../components/navBar/NavBar.component';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'home-page',
  templateUrl: './HomePage.html',
  standalone: true,
  imports: [NavBarComponent, RouterLink],
})
export class HomePage {}
