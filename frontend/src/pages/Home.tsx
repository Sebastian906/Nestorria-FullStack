import About from "../components/About"
import Cta from "../components/Cta"
import Faq from "../components/Faq"
import FeaturedProperties from "../components/FeaturedProperties"
import Hero from "../components/Hero"
import Testimonial from "../components/Testimonial"

const Home = () => {
    return (
        <div className='bg-linear-to-r from-green-50 to-white'>
            <Hero />
            <About />
            <FeaturedProperties />
            <Faq />
            <Cta />
            <Testimonial />
        </div>
    )
}

export default Home