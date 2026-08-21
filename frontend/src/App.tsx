import { Route, Routes } from "react-router-dom"
import Header from "./components/Header"
import Home from "./pages/Home"
import Listing from "./pages/Listing"
import Footer from "./components/Footer"
import Contact from "./pages/Contact"
import PropertyDetails from "./pages/PropertyDetails"
import MyBookings from "./pages/MyBookings"
import MapExplorer from "./pages/MapExplorer"
import { useAppContext } from "./context/AppContext"
import AgencyReg from "./components/AgencyReg"
import { Toaster } from "react-hot-toast"
import ContractDetails from "./pages/ContractDetails"
import Processing from "./pages/Processing"
import MyReviews from "./pages/MyReviews"
import MyHistory from "./pages/MyHistory"
import Agencies from "./pages/Agencies"
import Guides from "./pages/Guides"
import Compare from "./pages/Compare"

const App = () => {

  const { showAgencyReg } = useAppContext();

  return (
    <main>
      <Header />
      {showAgencyReg && <AgencyReg />}
      <Toaster position='bottom-right' />
      <Routes>
        <Route path='/' element={<Home />} />
        <Route path='/listing' element={<Listing />} />
        <Route path='/listing/:id' element={<PropertyDetails />} />
        <Route path='/guides' element={<Guides />} />
        <Route path='/agencies' element={<Agencies />} />
        <Route path='/contact' element={<Contact />} />
        <Route path='/map' element={<MapExplorer />} />
        <Route path='/compare' element={<Compare />} />
        <Route path='/my-bookings' element={<MyBookings />} />
        <Route path='/my-reviews' element={<MyReviews />} />
        <Route path='/my-history' element={<MyHistory />} />
        <Route path='/contracts/:id' element={<ContractDetails />} />
        <Route path='/processing/:nextUrl' element={<Processing />} />
      </Routes>
      <Footer />
    </main>
  )
}

export default App