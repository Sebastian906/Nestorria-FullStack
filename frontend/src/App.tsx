import { Route, Routes } from "react-router-dom"
import Header from "./components/Header"
import Home from "./pages/Home"
import Listing from "./pages/Listing"
import Footer from "./components/Footer"
import Blog from "./pages/Blog"
import Contact from "./pages/Contact"
import PropertyDetails from "./pages/PropertyDetails"

const App = () => {
  return (
    <main>
      <Header />
      <Routes>
        <Route path='/' element={<Home />} />
        <Route path='/listing' element={<Listing />} />
        <Route path='/listing/:id' element={<PropertyDetails />} />
        <Route path='/blog' element={<Blog />} />
        <Route path='/contact' element={<Contact />} />
      </Routes>
      <Footer />
    </main>
  )
}

export default App