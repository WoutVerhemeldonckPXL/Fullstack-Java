import { Routes } from '@angular/router';
import { HomePage } from './pages/landingpage/HomePage';
import { LoginPage } from './pages/login/LoginPage';
import { WeekMenuPage } from './pages/weekmenu/WeekMenuPage';
import { CreateBoxPage } from './pages/createbox/CreateBoxPage';
import { BestellingPage } from './pages/bestellingen/BestellingPage';

export const routes: Routes = [
  { path: '', component: HomePage },
  { path: 'login', component: LoginPage },
  { path: 'weekmenu', component: WeekMenuPage },
  { path: 'createbox', component: CreateBoxPage },
  { path: 'bestellingen', component: BestellingPage },
];
