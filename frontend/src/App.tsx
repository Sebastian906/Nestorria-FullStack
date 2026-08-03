import { Route, Routes } from "react-router-dom"
import Header from "./components/Header"
import Home from "./pages/Home"
import Listing from "./pages/Listing"
import Footer from "./components/Footer"
import Blog from "./pages/Blog"
import Contact from "./pages/Contact"
import PropertyDetails from "./pages/PropertyDetails"
import MyBookings from "./pages/MyBookings"
import MapExplorer from "./pages/MapExplorer"
import { useAppContext } from "./context/AppContext"
import AgencyReg from "./components/AgencyReg"
import { Toaster } from "react-hot-toast"
import ContractDetails from "./pages/ContractDetails"
import Processing from "./pages/Processing"

const App = () => {

  const { showAgencyReg } = useAppContext();

  return (
    <main>
      <Header />
      {showAgencyReg && <AgencyReg />}
      <Toaster position='bottom-right'/>
      <Routes>
        <Route path='/' element={<Home />} />
        <Route path='/listing' element={<Listing />} />
        <Route path='/listing/:id' element={<PropertyDetails />} />
        <Route path='/blog' element={<Blog />} />
        <Route path='/contact' element={<Contact />} />
        <Route path='/map' element={<MapExplorer />} />
        <Route path='/my-bookings' element={<MyBookings />} />
        <Route path='/contracts/:id' element={<ContractDetails />} />
        <Route path='/processing/:nextUrl' element={<Processing />} />
      </Routes>
      <Footer />
    </main>
  )
}

export default App