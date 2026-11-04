import ChecklistRounded from '@mui/icons-material/ChecklistRounded'
import ShoppingCartRounded from '@mui/icons-material/ShoppingCartRounded'
import ShoppingBasketRounded from '@mui/icons-material/ShoppingBasketRounded'
import RestaurantRounded from '@mui/icons-material/RestaurantRounded'
import PaymentsRounded from '@mui/icons-material/PaymentsRounded'
import SavingsRounded from '@mui/icons-material/SavingsRounded'
import ReceiptLongRounded from '@mui/icons-material/ReceiptLongRounded'
import MonitorHeartRounded from '@mui/icons-material/MonitorHeartRounded'
import MedicationRounded from '@mui/icons-material/MedicationRounded'
import FitnessCenterRounded from '@mui/icons-material/FitnessCenterRounded'
import DirectionsRunRounded from '@mui/icons-material/DirectionsRunRounded'
import SchoolRounded from '@mui/icons-material/SchoolRounded'
import MenuBookRounded from '@mui/icons-material/MenuBookRounded'
import HomeRounded from '@mui/icons-material/HomeRounded'
import CleaningServicesRounded from '@mui/icons-material/CleaningServicesRounded'
import HandymanRounded from '@mui/icons-material/HandymanRounded'
import YardRounded from '@mui/icons-material/YardRounded'
import PetsRounded from '@mui/icons-material/PetsRounded'
import ChildCareRounded from '@mui/icons-material/ChildCareRounded'
import FlightRounded from '@mui/icons-material/FlightRounded'
import type { SvgIconComponent } from '@mui/icons-material'
import type { ListIcon as ListIconToken } from '../api/lists'

/**
 * The curated set (R18), mirroring the server's ListIcon enum.
 *
 * Every icon is imported by its own path and mapped explicitly. @mui/icons-material
 * is roughly 11,000 modules, so a namespace import or a lookup by string here
 * is the one thing in this project that can put megabytes into a bundle served
 * to phones. See docs/adr/0014.
 */
const ICONS: Record<ListIconToken, { label: string; Icon: SvgIconComponent }> = {
  CHECKLIST: { label: 'Checklist', Icon: ChecklistRounded },
  SHOPPING_CART: { label: 'Shopping', Icon: ShoppingCartRounded },
  GROCERIES: { label: 'Groceries', Icon: ShoppingBasketRounded },
  RESTAURANT: { label: 'Meals', Icon: RestaurantRounded },
  PAYMENTS: { label: 'Money', Icon: PaymentsRounded },
  SAVINGS: { label: 'Saving', Icon: SavingsRounded },
  RECEIPT: { label: 'Bills', Icon: ReceiptLongRounded },
  HEALTH: { label: 'Health', Icon: MonitorHeartRounded },
  MEDICATION: { label: 'Medication', Icon: MedicationRounded },
  FITNESS: { label: 'Workout', Icon: FitnessCenterRounded },
  RUNNING: { label: 'Running', Icon: DirectionsRunRounded },
  SCHOOL: { label: 'School', Icon: SchoolRounded },
  READING: { label: 'Reading', Icon: MenuBookRounded },
  HOME: { label: 'Home', Icon: HomeRounded },
  CLEANING: { label: 'Cleaning', Icon: CleaningServicesRounded },
  REPAIRS: { label: 'Repairs', Icon: HandymanRounded },
  GARDEN: { label: 'Garden', Icon: YardRounded },
  PETS: { label: 'Pets', Icon: PetsRounded },
  CHILDREN: { label: 'Children', Icon: ChildCareRounded },
  TRAVEL: { label: 'Travel', Icon: FlightRounded },
}

export const DEFAULT_ICON: ListIconToken = 'CHECKLIST'

export const ICON_TOKENS = Object.keys(ICONS) as ListIconToken[]

export function iconLabel(token: ListIconToken): string {
  return ICONS[token]?.label ?? ICONS[DEFAULT_ICON].label
}

export function iconComponent(token: ListIconToken): SvgIconComponent {
  return (ICONS[token] ?? ICONS[DEFAULT_ICON]).Icon
}
